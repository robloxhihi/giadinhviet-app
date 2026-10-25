// Ghi chu giai SHA cua ban APK vao Firebase, tu dong sau moi lan build tag.
//
// Hai viec, both idempotent:
//   1. Firebase project settings -> Android app: them SHA-1/SHA-256 cua
//      debug keystore. Thieu 2 gia tri nay thi "Dang nhap Google" fail
//      DEVELOPER_ERROR ngay ca khi app dung tai khoan.
//   2. Firestore collection `release_hashes/{tag}`: SHA-1 + SHA-256 + kich
//      thuoc cua chinh file APK, de web/app doi chieu duoc file tai ve
//      co dung ban CI build ra hay khong.
//
// Chi dung khi repo co secret `FIREBASE_SA_JSON` (JSON cua service account
// co quyen Firebase API Client + Firestore Editor). Secret nam trong env cua
// luc chay, KHONG bao gio ghi xuong file hay commit.
//
// Service account di qua IAM, khong bi security rules chan, nen ghi duoc
// collection ma client app khong ghi duoc — day ly do viet bang REST hoi
// Firebase Admin, khong phai tu phia may.

import crypto from "node:crypto";
import fs from "node:fs";
import https from "node:https";

const SA_JSON = process.env.FIREBASE_SA_JSON;
if (!SA_JSON) {
  console.error("Thieu repo secret FIREBASE_SA_JSON — bo qua buoc ghi Firebase.");
  process.exit(0);
}

const APK = process.argv[2] || "GiaDinhViet.apk";
const TAG = process.argv[3] || (process.env.GITHUB_REF_NAME ?? "");
if (!fs.existsSync(APK)) {
  console.error(`Khong tim thay file APK ${APK}`);
  process.exit(1);
}

const sa = JSON.parse(SA_JSON);
const scopes = "https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/firebase";

// Runner cua GitHub Actions la Node 20 (khong co fetch), nen ty goi HTTPS
// bang module co san — tranh them dependency vao repo.
function request(rawUrl, init = {}) {
  return new Promise((res) => {
    const url = new URL(rawUrl);
    const body = init.body ? Buffer.from(init.body) : null;
    const headers = { "user-agent": "giadinhviet-ci", ...init.headers };
    if (body) headers["content-length"] = String(body.length);
    const rq = https.request(
      { hostname: url.hostname, path: url.pathname + url.search, method: init.method || "GET", headers },
      (r) => {
        let text = "";
        r.setEncoding("utf8");
        r.on("data", (c) => (text += c));
        r.on("end", () => res({ status: r.statusCode, text }));
      }
    );
    rq.on("error", (e) => res({ status: 0, text: String(e) }));
    if (body) rq.write(body);
    rq.end();
  });
}

const b64url = (buf) => Buffer.from(buf).toString("base64url");
const now = Math.floor(Date.now() / 1000);
const assertion = [
  b64url(JSON.stringify({ alg: "RS256", typ: "JWT" })),
  b64url(JSON.stringify({ iss: sa.client_email, scope: scopes, aud: sa.token_uri || "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600 }))
].join(".");
const signature = crypto.createSign("RSA-SHA256").update(assertion).sign(sa.private_key);
const jwt = `${assertion}.${signature.toString("base64url")}`;

const tokenRes = await request(sa.token_uri || "https://oauth2.googleapis.com/token", {
  method: "POST",
  headers: { "content-type": "application/x-www-form-urlencoded" },
  body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }).toString()
});
if (tokenRes.status !== 200) {
  console.error(`Lay token that bai (HTTP ${tokenRes.status}): ${tokenRes.text.slice(0, 300)}`);
  process.exit(1);
}
const { access_token } = JSON.parse(tokenRes.text);
const auth = { authorization: `Bearer ${access_token}` };
const PROJECT = sa.project_id;

const apkBuf = fs.readFileSync(APK);
const sha256hex = crypto.createHash("sha256").update(apkBuf).digest("hex");
const sha1hex = crypto.createHash("sha1").update(apkBuf).digest("hex");
const colon = (hex) => hex.toUpperCase().match(/.{2}/g).join(":");
console.log(`APK ${APK}: ${apkBuf.length} bytes, sha256=${sha256hex}`);

// Chu ky cua chinh ban APK (khac voi SHA cua file): lay tu file APK da build.
// Firebase can gia tri nay cho "Dang nhap Google", khong phai SHA cua file.
const certInfo = (() => {
  const p = process.env.CERT_SHA1;
  return p ? { sha1: p, sha256: process.env.CERT_SHA256 || "" } : null;
})();
if (!certInfo) {
  console.error("Chua truyen CERT_SHA1/CERT_SHA256 (chu ky debug keystore) — chi ghi SHA cua file APK.");
}

async function j(url, init = {}) {
  const r = await request(url, { ...init, headers: { ...auth, ...(init.headers || {}) } });
  let json = null;
  try { json = JSON.parse(r.text); } catch { /* giu nguyen text de in ra loi */ }
  return { status: r.status, text: r.text, json };
}

// --- 1. Firebase: SHA-1/SHA-256 cua chu ky vao Android app config ---------
if (certInfo) {
  const list = await j(`https://firebase.googleapis.com/v1beta1/projects/${PROJECT}/androidApps?pageSize=50`);
  if (list.status !== 200) {
    console.error(`Liệt kê Android app that bai (HTTP ${list.status}): ${list.text.slice(0, 300)}`);
  } else {
    for (const app of list.json.apps || list.json.resources || []) {
      const cur = app.certificateHashes || [];
      const add = [certInfo.sha1, certInfo.sha256].filter((h) => h && !cur.includes(h));
      if (!add.length) {
        console.log(`Android app ${app.packageName} da co du fingerprint — khong sua.`);
        continue;
      }
      const next = [...cur, ...add];
      const patch = await j(
        `https://firebase.googleapis.com/v1beta1/projects/${PROJECT}/androidApps/${app.appId}?updateMask=certificateHashes`,
        { method: "PATCH", headers: { "content-type": "application/json" }, body: JSON.stringify({ certificateHashes: next }) }
      );
      console.log(`Cap nhat fingerprint cho ${app.packageName} (HTTP ${patch.status})${patch.status === 200 ? "" : ": " + patch.text.slice(0, 200)}`);
    }
  }
}

// --- 2. Firestore: SHA cua file APK theo tung release ---------------------
const dbs = await j(`https://firestore.googleapis.com/v1/projects/${PROJECT}/databases?pageSize=50`);
if (dbs.status !== 200) {
  console.error(`Liệt kê database that bai (HTTP ${dbs.status}): ${dbs.text.slice(0, 300)}`);
  process.exit(1);
}
// Project nay dung NAMED database, khong phai "(default)" — hai app Kotlin
// cung layFirestore(DATABASE_ID). Truoc day script cham [0] trong danh sach
// nen co the ghi nham vao "(default)" (rong), khong ai doc. Uu tien: env
// FIRESTORE_DB_ID -> database dau tien ten bat dau "ai-studio-android-vespera"
// -> "(default)" -> cai dau tien.
const want = process.env.FIRESTORE_DB_ID || "ai-studio-android-vespera-f0b8eaec-ca07-4549-bb58-a45233ead3c3";
const ids = (dbs.json.databases || []).map((d) => d.databaseId);
const dbId = ids.includes(want) ? want : (ids.find((id) => id.startsWith("ai-studio-android-vespera")) ?? ids[0]);
if (!dbId) {
  console.error("Project khong co database Firestore nao.");
  process.exit(1);
}
console.log(`Database: ${dbId} (danh sach: ${ids.join(", ")})`);

const doc = {
  fields: {
    tag: { stringValue: TAG },
    apkFileName: { stringValue: APK },
    sizeBytes: { integerValue: String(apkBuf.length) },
    sha256: { stringValue: sha256hex },
    sha1: { stringValue: sha1hex },
    apkUrl: {
      stringValue: `https://github.com/${process.env.GITHUB_REPOSITORY || "robloxhihi/giadinhviet-app"}/releases/download/${TAG}/${APK}`
    },
    signerSha1: { stringValue: certInfo ? certInfo.sha1 : "" },
    signerSha256: { stringValue: certInfo ? certInfo.sha256 : "" },
    builtBy: { stringValue: `GitHub Actions run ${process.env.GITHUB_RUN_ID || "?"}` },
    verifiedAt: { timestampValue: new Date().toISOString() }
  }
};
const base = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/${dbId}/documents/release_hashes`;
let w = await j(`${base}?documentId=${encodeURIComponent(TAG)}`, {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: JSON.stringify(doc)
});
if (w.status === 409) {
  w = await j(`${base}/${encodeURIComponent(TAG)}`, {
    method: "PATCH",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(doc)
  });
}
console.log(`release_hashes/${TAG} -> HTTP ${w.status}${w.status >= 300 ? ": " + w.text.slice(0, 300) : ""}`);
if (w.status >= 300) process.exit(1);
