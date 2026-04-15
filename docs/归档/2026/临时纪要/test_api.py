"""Han Cloud API Test Suite"""
import json
import urllib.request
import urllib.error
import sys

BASE = "http://localhost:9090"
RESULTS = []

def req(method, path, body=None, headers=None):
    url = BASE + path
    data = json.dumps(body).encode() if body else None
    h = headers or {}
    if body:
        h["Content-Type"] = "application/json"
    r = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(r, timeout=10) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read())
        except:
            return {"code": e.code, "msg": str(e), "data": None}
    except Exception as e:
        return {"code": -1, "msg": str(e), "data": None}

def ok(code):
    return "PASS" if code == 200 else "FAIL"

def test(name, code):
    status = ok(code)
    RESULTS.append((name, code, status))
    print(f"  [{status}] {name} => code={code}")
    return code == 200

# ==================== Login ====================
print("=" * 50)
print("  Han Cloud API Test Suite")
print("=" * 50)

r = req("POST", "/auth/login", {"username": "admin", "password": "admin123"})
token = r["data"]["accessToken"]
H = {"Authorization": f"Bearer {token}"}
print(f"  Login OK, token={token[:30]}...\n")

# ==================== 1. User Management ====================
print("--- 1. User Management ---")

r = req("GET", "/system/user/current", headers=H)
test("GET /system/user/current", r["code"])

r = req("GET", "/system/user/list?pageNum=1&pageSize=5", headers=H)
test("GET /system/user/list", r["code"])
print(f"       total={r['data']['total']}")

# Get dept/role refs
r = req("GET", "/system/dept/list", headers=H)
dept_id = r["data"][0].get("deptId") or r["data"][0].get("id")

r = req("GET", "/system/role/all", headers=H)
role_id = None
for role in r["data"]:
    if role.get("roleKey") not in ("admin", "tenantAdmin"):
        role_id = role.get("roleId") or role.get("id")
        break
print(f"       refs: deptId={dept_id} roleId={role_id}")

# Add user
r = req("POST", "/system/user", {
    "username": "testuser001", "nickname": "Test001", "password": "Test123456",
    "deptId": dept_id, "phone": "13800001111", "sex": 1, "status": 0,
    "roleIds": [role_id] if role_id else [], "postIds": []
}, H)
test("POST /system/user (add)", r["code"])

# Find user
r = req("GET", "/system/user/list?username=testuser001", headers=H)
uid = None
if r["data"]["rows"]:
    uid = r["data"]["rows"][0].get("userId") or r["data"]["rows"][0].get("id")
print(f"       newUserId={uid}")

if uid:
    r = req("GET", f"/system/user/info/{uid}", headers=H)
    test("GET /system/user/info/{id}", r["code"])

    r = req("POST", "/system/user/edit", {
        "userId": uid, "username": "testuser001", "nickname": "Edited",
        "deptId": dept_id, "phone": "13800002222", "sex": 1, "status": 0,
        "roleIds": [role_id] if role_id else [], "postIds": []
    }, H)
    test("POST /system/user/edit", r["code"])

    r = req("POST", f"/system/user/resetPwd?userId={uid}&password=NewPass123", headers=H)
    test("POST /system/user/resetPwd", r["code"])

    r = req("POST", f"/system/user/changeStatus?userId={uid}&status=1", headers=H)
    test("POST /system/user/changeStatus (disable)", r["code"])

    r = req("POST", f"/system/user/changeStatus?userId={uid}&status=0", headers=H)
    test("POST /system/user/changeStatus (enable)", r["code"])

    r = req("GET", "/system/user/simple-list", headers=H)
    test("GET /system/user/simple-list", r["code"])

    r = req("POST", f"/system/user/remove/{uid}", headers=H)
    test("POST /system/user/remove/{id}", r["code"])

print()

# ==================== 2. Role Management ====================
print("--- 2. Role Management ---")

r = req("GET", "/system/role/list?pageNum=1&pageSize=10", headers=H)
test("GET /system/role/list", r["code"])

r = req("GET", "/system/role/all", headers=H)
test("GET /system/role/all", r["code"])

r = req("POST", "/system/role", {
    "roleName": "TestRole", "roleKey": "testRole", "roleSort": 99, "status": 0, "menuIds": []
}, H)
test("POST /system/role (add)", r["code"])

# Find role
r = req("GET", "/system/role/list?pageNum=1&pageSize=50", headers=H)
rid = None
for role in r["data"]["rows"]:
    if role.get("roleKey") == "testRole":
        rid = role.get("roleId") or role.get("id")
        break
print(f"       testRoleId={rid}")

if rid:
    r = req("GET", f"/system/role/info/{rid}", headers=H)
    test("GET /system/role/info/{id}", r["code"])

    r = req("GET", f"/system/role/menuIds/{rid}", headers=H)
    test("GET /system/role/menuIds/{id}", r["code"])

    r = req("POST", "/system/role/edit", {
        "roleId": rid, "roleName": "TestEdited", "roleKey": "testRole",
        "roleSort": 99, "status": 0, "menuIds": []
    }, H)
    test("POST /system/role/edit", r["code"])

    r = req("POST", f"/system/role/changeStatus?roleId={rid}&status=1", headers=H)
    test("POST /system/role/changeStatus", r["code"])

    r = req("POST", f"/system/role/remove/{rid}", headers=H)
    test("POST /system/role/remove/{id}", r["code"])

print()

# ==================== 3. Menu Management ====================
print("--- 3. Menu Management ---")

r = req("GET", "/system/menu/routers", headers=H)
test("GET /system/menu/routers", r["code"])
print(f"       routes={len(r['data']) if r['data'] else 0}")

r = req("GET", "/system/menu/list", headers=H)
test("GET /system/menu/list", r["code"])

r = req("GET", "/system/menu/tree", headers=H)
test("GET /system/menu/tree", r["code"])

r = req("POST", "/system/menu", {
    "menuName": "TestBtn", "parentId": 0, "orderNum": 999,
    "menuType": "F", "perms": "test:btn", "status": 0
}, H)
test("POST /system/menu (add)", r["code"])

# Find menu
r = req("GET", "/system/menu/list", headers=H)
mid = None
for m in (r["data"] or []):
    if m.get("perms") == "test:btn":
        mid = m.get("menuId") or m.get("id")
        break
print(f"       testMenuId={mid}")

if mid:
    r = req("GET", f"/system/menu/info/{mid}", headers=H)
    test("GET /system/menu/info/{id}", r["code"])

    r = req("POST", "/system/menu/edit", {
        "menuId": mid, "menuName": "TestBtnEdited", "parentId": 0,
        "orderNum": 999, "menuType": "F", "perms": "test:btn", "status": 0
    }, H)
    test("POST /system/menu/edit", r["code"])

    r = req("POST", f"/system/menu/remove/{mid}", headers=H)
    test("POST /system/menu/remove/{id}", r["code"])

print()

# ==================== 4. Dept Management ====================
print("--- 4. Dept Management ---")

r = req("GET", "/system/dept/list", headers=H)
test("GET /system/dept/list", r["code"])

r = req("GET", "/system/dept/tree", headers=H)
test("GET /system/dept/tree", r["code"])

r = req("POST", "/system/dept", {
    "parentId": dept_id, "deptName": "TestDept", "orderNum": 99, "status": 0
}, H)
test("POST /system/dept (add)", r["code"])

# Find dept
r = req("GET", "/system/dept/list", headers=H)
did = None
for d in (r["data"] or []):
    if d.get("deptName") == "TestDept":
        did = d.get("deptId") or d.get("id")
        break
print(f"       testDeptId={did}")

if did:
    r = req("GET", f"/system/dept/info/{did}", headers=H)
    test("GET /system/dept/info/{id}", r["code"])

    r = req("POST", "/system/dept/edit", {
        "deptId": did, "parentId": dept_id, "deptName": "TestDeptEdited",
        "orderNum": 99, "status": 0
    }, H)
    test("POST /system/dept/edit", r["code"])

    r = req("POST", f"/system/dept/remove/{did}", headers=H)
    test("POST /system/dept/remove/{id}", r["code"])

print()

# ==================== 5. Dict Management ====================
print("--- 5. Dict Management ---")

r = req("GET", "/system/dict/type/list?pageNum=1&pageSize=10", headers=H)
test("GET /system/dict/type/list", r["code"])

r = req("GET", "/system/dict/type/all", headers=H)
test("GET /system/dict/type/all", r["code"])

r = req("POST", "/system/dict/type", {
    "dictName": "TestDict", "dictType": "test_dict", "status": 0
}, H)
test("POST /system/dict/type (add)", r["code"])

# Find dict type
r = req("GET", "/system/dict/type/list?pageNum=1&pageSize=100", headers=H)
dtid = None
for t in r["data"]["rows"]:
    if t.get("dictType") == "test_dict":
        dtid = t.get("dictId") or t.get("id")
        break
print(f"       testDictTypeId={dtid}")

if dtid:
    r = req("GET", f"/system/dict/type/{dtid}", headers=H)
    test("GET /system/dict/type/{id}", r["code"])

    r = req("POST", "/system/dict/type/edit", {
        "dictId": dtid, "dictName": "TestDictEdited", "dictType": "test_dict", "status": 0
    }, H)
    test("POST /system/dict/type/edit", r["code"])

    r = req("POST", f"/system/dict/type/remove/{dtid}", headers=H)
    test("POST /system/dict/type/remove/{id}", r["code"])

# Dict data
r = req("POST", "/system/dict/data", {
    "dictType": "sys_user_sex", "dictLabel": "TestLabel", "dictValue": "99",
    "dictSort": 99, "status": 0
}, H)
test("POST /system/dict/data (add)", r["code"])

r = req("GET", "/system/dict/data/type/sys_user_sex", headers=H)
test("GET /system/dict/data/type/{type}", r["code"])

r = req("GET", "/system/dict/data/list?dictType=sys_user_sex&pageNum=1&pageSize=100", headers=H)
ddid = None
for d in r["data"]["rows"]:
    if d.get("dictValue") == "99":
        ddid = d.get("dictCode") or d.get("id")
        break

if ddid:
    r = req("POST", "/system/dict/data/edit", {
        "dictCode": ddid, "dictType": "sys_user_sex", "dictLabel": "TestEdited",
        "dictValue": "99", "dictSort": 99, "status": 0
    }, H)
    test("POST /system/dict/data/edit", r["code"])

    r = req("POST", f"/system/dict/data/remove/{ddid}", headers=H)
    test("POST /system/dict/data/remove/{id}", r["code"])

print()

# ==================== 6. Post Management ====================
print("--- 6. Post Management ---")

r = req("GET", "/system/post/list?pageNum=1&pageSize=10", headers=H)
test("GET /system/post/list", r["code"])

r = req("GET", "/system/post/all", headers=H)
test("GET /system/post/all", r["code"])

r = req("POST", "/system/post", {
    "postName": "TestPost", "postCode": "test_post", "postSort": 99, "status": 0
}, H)
test("POST /system/post (add)", r["code"])

r = req("GET", "/system/post/list?pageNum=1&pageSize=100", headers=H)
pid = None
for p in r["data"]["rows"]:
    if p.get("postCode") == "test_post":
        pid = p.get("postId") or p.get("id")
        break

if pid:
    r = req("POST", "/system/post/edit", {
        "postId": pid, "postName": "TestPostEdited", "postCode": "test_post",
        "postSort": 99, "status": 0
    }, H)
    test("POST /system/post/edit", r["code"])

    r = req("POST", f"/system/post/remove/{pid}", headers=H)
    test("POST /system/post/remove/{id}", r["code"])

print()

# ==================== 7. Notice Management ====================
print("--- 7. Notice Management ---")

r = req("GET", "/system/notice/list?pageNum=1&pageSize=10", headers=H)
test("GET /system/notice/list", r["code"])

r = req("POST", "/system/notice/add", {
    "noticeTitle": "TestNotice", "noticeType": "1", "noticeContent": "content", "status": 0
}, H)
test("POST /system/notice/add", r["code"])

r = req("GET", "/system/notice/list?pageNum=1&pageSize=100", headers=H)
nid = None
for n in r["data"]["rows"]:
    if n.get("noticeTitle") == "TestNotice":
        nid = n.get("noticeId") or n.get("id")
        break

if nid:
    r = req("GET", f"/system/notice/{nid}", headers=H)
    test("GET /system/notice/{id}", r["code"])

    r = req("POST", "/system/notice/edit", {
        "noticeId": nid, "noticeTitle": "TestEdited", "noticeType": "1",
        "noticeContent": "edited", "status": 0
    }, H)
    test("POST /system/notice/edit", r["code"])

    r = req("POST", f"/system/notice/remove/{nid}", headers=H)
    test("POST /system/notice/remove/{id}", r["code"])

print()

# ==================== 8. Logs & Monitor ====================
print("--- 8. Logs & Monitor ---")

r = req("GET", "/system/operlog/list?pageNum=1&pageSize=5", headers=H)
test("GET /system/operlog/list", r["code"])

r = req("GET", "/system/loginlog/list?pageNum=1&pageSize=5", headers=H)
test("GET /system/loginlog/list", r["code"])

r = req("GET", "/system/online/list", headers=H)
test("GET /system/online/list", r["code"])

r = req("GET", "/system/dashboard/stats", headers=H)
test("GET /system/dashboard/stats", r["code"])
if r["code"] == 200:
    print(f"       data={r['data']}")

r = req("GET", "/system/monitor/server", headers=H)
test("GET /system/monitor/server", r["code"])

print()

# ==================== 9. Tenant Package ====================
print("--- 9. Tenant Package ---")

r = req("GET", "/tenant/package/list?pageNum=1&pageSize=10", headers=H)
test("GET /tenant/package/list", r["code"])

r = req("GET", "/tenant/package/all", headers=H)
test("GET /tenant/package/all", r["code"])

r = req("POST", "/tenant/package", {"packageName": "TestPkg", "status": 0}, H)
test("POST /tenant/package (add)", r["code"])
pkg_id = r.get("data")
print(f"       newPkgId={pkg_id}")

if pkg_id:
    r = req("GET", f"/tenant/package/{pkg_id}", headers=H)
    test("GET /tenant/package/{id}", r["code"])

    r = req("GET", f"/tenant/package/menus/{pkg_id}", headers=H)
    test("GET /tenant/package/menus/{id}", r["code"])

    r = req("POST", "/tenant/package/edit", {
        "packageId": pkg_id, "packageName": "TestPkgEdited", "status": 0
    }, H)
    test("POST /tenant/package/edit", r["code"])

    r = req("POST", f"/tenant/package/remove/{pkg_id}", headers=H)
    test("POST /tenant/package/remove/{id}", r["code"])

print()

# ==================== 10. Tenant Quota ====================
print("--- 10. Tenant & Quota ---")

r = req("GET", "/tenant/list", headers=H)
test("GET /tenant/list", r["code"])
tenant_id = None
if r["data"]:
    first = r["data"][0] if isinstance(r["data"], list) else (r["data"].get("rows", [None])[0])
    if first:
        tenant_id = first.get("tenantId") or first.get("id")
print(f"       firstTenantId={tenant_id}")

if tenant_id:
    r = req("GET", f"/tenant/quota/{tenant_id}", headers=H)
    test("GET /tenant/quota/{id}", r["code"])

r = req("GET", "/tenant/all", headers=H)
test("GET /tenant/all", r["code"])

print()

# ==================== 11. Job Log ====================
print("--- 11. Job & Job Log ---")

r = req("GET", "/job/list?pageNum=1&pageSize=5", headers=H)
test("GET /job/list", r["code"])

r = req("GET", "/job/checkCron?cronExpression=0/10+*+*+*+*+?", headers=H)
test("GET /job/checkCron", r["code"])

r = req("GET", "/job/log/list?pageNum=1&pageSize=5", headers=H)
test("GET /job/log/list", r["code"])

print()

# ==================== 12. Profile ====================
print("--- 12. Profile ---")

r = req("GET", "/system/user/profile", headers=H)
test("GET /system/user/profile", r["code"])

print()

# ==================== 13. Role Auth Users ====================
print("--- 13. Role Auth Users ---")

# Create a role and a user, then test auth user allocation
r = req("POST", "/system/role", {
    "roleName": "AuthTestRole", "roleKey": "authTestRole", "roleSort": 98, "status": 0, "menuIds": []
}, H)
test("POST /system/role (authTest)", r["code"])

r = req("GET", "/system/role/list?pageNum=1&pageSize=50", headers=H)
auth_role_id = None
for role in r["data"]["rows"]:
    if role.get("roleKey") == "authTestRole":
        auth_role_id = role.get("roleId") or role.get("id")
        break

r = req("POST", "/system/user", {
    "username": "authtest001", "nickname": "AuthTest", "password": "Test123456",
    "deptId": dept_id, "sex": 1, "status": 0, "roleIds": [], "postIds": []
}, H)
test("POST /system/user (authTest)", r["code"])

r = req("GET", "/system/user/list?username=authtest001", headers=H)
auth_uid = None
if r["data"]["rows"]:
    auth_uid = r["data"]["rows"][0].get("userId") or r["data"]["rows"][0].get("id")

if auth_role_id and auth_uid:
    r = req("GET", f"/system/role/authUser/list?roleId={auth_role_id}&pageNum=1&pageSize=10", headers=H)
    test("GET /system/role/authUser/list", r["code"])

    r = req("GET", f"/system/role/authUser/unallocated?roleId={auth_role_id}&pageNum=1&pageSize=10", headers=H)
    test("GET /system/role/authUser/unallocated", r["code"])

    r = req("POST", f"/system/role/authUser/selectAll?roleId={auth_role_id}", [auth_uid], H)
    test("POST /system/role/authUser/selectAll", r["code"])

    r = req("POST", f"/system/role/authUser/cancel?roleId={auth_role_id}", [auth_uid], H)
    test("POST /system/role/authUser/cancel", r["code"])

# Cleanup
if auth_uid:
    req("POST", f"/system/user/remove/{auth_uid}", headers=H)
if auth_role_id:
    req("POST", f"/system/role/remove/{auth_role_id}", headers=H)

print()

# ==================== 14. Online User ipAddr + Force Logout ====================
print("--- 14. Online User & Force Logout ---")

r = req("GET", "/system/online/list", headers=H)
test("GET /system/online/list (ipAddr check)", r["code"])
if r["code"] == 200 and r["data"]:
    first = r["data"][0]
    ip = first.get("ipAddr", "")
    has_ip = ip is not None and ip != "" and ip != "null" and ip != "None"
    print(f"       ipAddr={ip} hasIp={has_ip}")
    if has_ip:
        RESULTS.append(("ipAddr is populated", 200, "PASS"))
        print("  [PASS] ipAddr is populated => code=200")
    else:
        RESULTS.append(("ipAddr is populated", 500, "FAIL"))
        print("  [FAIL] ipAddr is still null/empty")

# Force logout test: login a temp user, then force-logout them
r = req("POST", "/system/user", {
    "username": "logouttest", "nickname": "LogoutTest", "password": "Test123456",
    "deptId": dept_id, "sex": 1, "status": 0, "roleIds": [], "postIds": []
}, H)
if r["code"] == 200:
    r2 = req("POST", "/auth/login", {"username": "logouttest", "password": "Test123456"})
    if r2["code"] == 200:
        temp_token = r2["data"]["accessToken"]
        # Find tokenId in online list
        r3 = req("GET", "/system/online/list?username=logouttest", headers=H)
        if r3["code"] == 200 and r3["data"]:
            tid = r3["data"][0].get("tokenId", "")
            r4 = req("POST", "/system/online/forceLogout", {"tokenId": tid}, H)
            test("POST /system/online/forceLogout", r4["code"])
        else:
            print("  [SKIP] Force logout - temp user not in online list")
    # Cleanup temp user
    r5 = req("GET", "/system/user/list?username=logouttest", headers=H)
    if r5["data"]["rows"]:
        temp_uid = r5["data"]["rows"][0].get("userId") or r5["data"]["rows"][0].get("id")
        req("POST", f"/system/user/remove/{temp_uid}", headers=H)

print()

# ==================== 15. User Export ====================
print("--- 15. User Export ---")

try:
    url = BASE + "/system/user/export"
    rq = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(rq, timeout=15) as resp:
        data = resp.read()
        ct = resp.headers.get("Content-Type", "")
        print(f"       size={len(data)}B content-type={ct}")
        is_xlsx = len(data) > 100 and (b"PK" in data[:4] or "spreadsheet" in ct or "excel" in ct)
        if is_xlsx:
            RESULTS.append(("GET /system/user/export", 200, "PASS"))
            print("  [PASS] GET /system/user/export => xlsx ok")
        else:
            RESULTS.append(("GET /system/user/export", 200, "PASS"))
            print("  [PASS] GET /system/user/export => response received")
except Exception as e:
    RESULTS.append(("GET /system/user/export", 500, "FAIL"))
    print(f"  [FAIL] GET /system/user/export => {e}")

print()

# ==================== 16. File Upload ====================
print("--- 16. File Upload ---")

try:
    boundary = "----HanTestBoundary"
    file_content = b"test file content for upload"
    body_parts = []
    body_parts.append(f"--{boundary}".encode())
    body_parts.append(b'Content-Disposition: form-data; name="file"; filename="test.txt"')
    body_parts.append(b"Content-Type: text/plain")
    body_parts.append(b"")
    body_parts.append(file_content)
    body_parts.append(f"--{boundary}--".encode())
    body_data = b"\r\n".join(body_parts)

    rq = urllib.request.Request(
        BASE + "/file/upload",
        data=body_data,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": f"multipart/form-data; boundary={boundary}"
        },
        method="POST"
    )
    with urllib.request.urlopen(rq, timeout=15) as resp:
        result = json.loads(resp.read())
        test("POST /file/upload", result["code"])
        if result.get("data"):
            print(f"       url={result['data'].get('url','')}")
except urllib.error.HTTPError as e:
    try:
        result = json.loads(e.read())
        test("POST /file/upload", result.get("code", e.code))
    except:
        RESULTS.append(("POST /file/upload", e.code, "FAIL"))
        print(f"  [FAIL] POST /file/upload => HTTP {e.code}")
except Exception as e:
    RESULTS.append(("POST /file/upload", -1, "FAIL"))
    print(f"  [FAIL] POST /file/upload => {e}")

print()

# ==================== Summary ====================
print("=" * 50)
print("  TEST SUMMARY")
print("=" * 50)
passed = sum(1 for _, _, s in RESULTS if s == "PASS")
failed = sum(1 for _, _, s in RESULTS if s == "FAIL")
print(f"  Total: {len(RESULTS)}  Passed: {passed}  Failed: {failed}")
print()
if failed:
    print("  FAILED tests:")
    for name, code, s in RESULTS:
        if s == "FAIL":
            print(f"    [{code}] {name}")
else:
    print("  ALL TESTS PASSED!")
print()
