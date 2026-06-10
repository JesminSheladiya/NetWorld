import { useState, useEffect } from "react";
import { Modal, Form, Input, Button, message, Avatar, Upload, Tabs, Spin, Select } from "antd";
import { UserOutlined, PhoneOutlined, LockOutlined, EditOutlined, CameraOutlined, SearchOutlined } from "@ant-design/icons";
import ImgCrop from "antd-img-crop";
import { updateProfile, getUser } from "../Services/authService";
import { http } from "../Services/https";

const BASE         = process.env.REACT_APP_API_URL?.replace("/api/contacts", "/api") || "http://localhost:8080/api";
const CONTACTS_URL = process.env.REACT_APP_API_URL || "http://localhost:8080/api/contacts";

const COLORS = ["#6366f1","#0ea5e9","#10b981","#f59e0b","#8b5cf6","#ec4899"];
const avatarBg = (name = "") => COLORS[(name.charCodeAt(0) || 0) % COLORS.length];

const relStyle = (rel = "") => {
    const r = rel.toLowerCase();
    if (r.includes("brother") || r.includes("sister"))  return { color: "#60a5fa", bg: "rgba(96,165,250,0.1)"  };
    if (r.includes("father")  || r.includes("mother"))  return { color: "#a78bfa", bg: "rgba(167,139,250,0.1)" };
    if (r.includes("son")     || r.includes("daughter"))return { color: "#34d399", bg: "rgba(52,211,153,0.1)"  };
    if (r.includes("grand"))                             return { color: "#fbbf24", bg: "rgba(251,191,36,0.1)"  };
    if (r.includes("husband") || r.includes("wife"))    return { color: "#f472b6", bg: "rgba(244,114,182,0.1)" };
    return                                                      { color: "#22d3ee", bg: "rgba(34,211,238,0.1)"  };
};

const Chip = ({ rel }) => {
    if (!rel) return null;
    const s = relStyle(rel);
    return (
        <span style={{
            color: s.color, background: s.bg, border: `1px solid ${s.color}30`,
            borderRadius: 5, padding: "1px 8px", fontSize: 11, fontWeight: 600, whiteSpace: "nowrap",
        }}>
            {rel.charAt(0).toUpperCase() + rel.slice(1).toLowerCase()}
        </span>
    );
};

const Usr = ({ name, pic, size = 36 }) => (
    <Avatar size={size} src={pic || null}
        style={{ backgroundColor: pic ? "transparent" : avatarBg(name || ""), fontWeight: 700, flexShrink: 0 }}>
        {!pic && (name?.charAt(0) || "?").toUpperCase()}
    </Avatar>
);

const Row = ({ name, email, pic, rel, reason, actions }) => (
    <div style={{
        display: "flex", alignItems: "center", gap: 10,
        padding: "8px 0", borderBottom: "1px solid rgba(255,255,255,0.05)",
    }}>
        <Usr name={name} pic={pic} />
        <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ color: "#e2e8f0", fontSize: 13, fontWeight: 500, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{name}</div>
            {reason
                ? <div style={{ color: "#475569", fontSize: 11, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{reason}</div>
                : email && <div style={{ color: "#475569", fontSize: 11 }}>{email}</div>
            }
        </div>
        {rel && <Chip rel={rel} />}
        {actions && <div style={{ display: "flex", gap: 5, flexShrink: 0 }}>{actions}</div>}
    </div>
);

const Btn = ({ onClick, accept }) => (
    <button onClick={onClick} style={{
        width: 26, height: 26, borderRadius: 5, cursor: "pointer", fontSize: 12, border: "none",
        background: accept ? "rgba(16,185,129,0.2)" : "rgba(239,68,68,0.15)",
        color: accept ? "#10b981" : "#ef4444",
    }}>
        {accept ? "✓" : "✕"}
    </button>
);

const Empty = ({ text }) => (
    <div style={{ textAlign: "center", padding: "24px 0", color: "#334155", fontSize: 12 }}>{text}</div>
);

const inputStyle = {
    background: "#0d1117", borderColor: "rgba(99,102,241,0.3)",
    color: "#f8fafc", height: 40, borderRadius: 8, fontSize: 13,
};

function UserProfile({ open, onClose, onProfileUpdate }) {
    const user = getUser();

    const [editing, setEditing]     = useState(false);
    const [saving,  setSaving]      = useState(false);
    const [form]                    = Form.useForm();
    const [preview, setPreview]     = useState(user.profilePicture || null);
    const [newImg,  setNewImg]      = useState(null);

    const [connections,  setConnections]  = useState([]);
    const [pending,      setPending]      = useState([]);
    const [suggestions,  setSuggestions]  = useState([]);
    const [relations,    setRelations]    = useState([]);
    const [loading,      setLoading]      = useState(false);

    const [query,       setQuery]       = useState("");
    const [results,     setResults]     = useState([]);
    const [searching,   setSearching]   = useState(false);
    const [relMap,      setRelMap]      = useState({});
    const [sendingMap,  setSendingMap]  = useState({});
    const [sentMap,     setSentMap]     = useState({});

    useEffect(() => {
        if (open) { fetchAll(); fetchRelations(); }
        else { setQuery(""); setResults([]); setSentMap({}); setRelMap({}); }
    }, [open]);

    const fetchAll = async () => {
        setLoading(true);
        try {
            const [c, p, s] = await Promise.all([
                http.get(`${BASE}/user-relations/connections`),
                http.get(`${BASE}/user-relations/pending`),
                http.get(`${BASE}/user-relations/suggestions`),
            ]);
            setConnections(c.data); setPending(p.data); setSuggestions(s.data);
        } catch { message.error("Failed to load!"); }
        finally { setLoading(false); }
    };

    const fetchRelations = async () => {
        try { const r = await http.get(`${CONTACTS_URL}/relations`); setRelations(r.data); }
        catch { /* silent */ }
    };

    const search = async () => {
        if (!query.trim()) return;
        setSearching(true); setResults([]);
        try {
            const res = await http.get(`${BASE}/user-relations/search-users?query=${encodeURIComponent(query)}`);
            setResults(res.data);
        } catch { message.error("Search failed!"); }
        finally { setSearching(false); }
    };

    const sendRequest = async (email) => {
        if (!relMap[email]) { message.warning("Select a relation first!"); return; }
        setSendingMap(p => ({ ...p, [email]: true }));
        try {
            await http.post(`${BASE}/user-relations/send`, { toEmail: email, relationId: relMap[email] });
            setSentMap(p => ({ ...p, [email]: true }));
            fetchAll();
        } catch (e) { message.error(e.response?.data?.message || "Failed!"); }
        finally { setSendingMap(p => ({ ...p, [email]: false })); }
    };

    const acceptPending  = async (id) => { try { await http.post(`${BASE}/user-relations/${id}/accept`);  fetchAll(); } catch { message.error("Failed!"); } };
    const declinePending = async (id) => { try { await http.post(`${BASE}/user-relations/${id}/decline`); fetchAll(); } catch { message.error("Failed!"); } };

    const acceptSuggestion = async (s) => {
        try {
            await http.post(`${BASE}/user-relations/suggestions/accept`, {
                otherEmail: s.suggestedUserEmail, relationName: s.inferredRelation,
            });
            fetchAll();
        } catch { message.error("Failed!"); }
    };

    const dismissSuggestion = (s) =>
        setSuggestions(p => p.filter(x => x.suggestedUserEmail !== s.suggestedUserEmail));

    const save = async (values) => {
        setSaving(true);
        try {
            const updated = await updateProfile({
                fullName: values.fullName, phone: values.phone,
                currentPassword: values.currentPassword, newPassword: values.newPassword,
                ...(newImg !== null && { profilePicture: newImg }),
            });
            message.success("Saved!"); setEditing(false); onProfileUpdate(updated);
        } catch (e) { message.error(e.response?.data?.message || "Failed!"); }
        finally { setSaving(false); }
    };

    const tabItems = [
        {
            key: "profile",
            label: "Profile",
            children: (
                <div>
                    <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 16 }}>
                        <Usr name={user.fullName || user.username} pic={user.profilePicture} size={48} />
                        <div>
                            <div style={{ color: "#f1f5f9", fontWeight: 700, fontSize: 15 }}>{user.fullName || user.username}</div>
                            <div style={{ color: "#475569", fontSize: 12 }}>{user.email}</div>
                        </div>
                    </div>

                    {[
                        ["Phone",    user.phone || "—"],
                        ["Username", user.username],
                        ["Role",     user.role || "USER"],
                    ].map(([l, v]) => (
                        <div key={l} style={{
                            display: "flex", justifyContent: "space-between",
                            padding: "7px 0", borderBottom: "1px solid rgba(255,255,255,0.05)",
                            fontSize: 12,
                        }}>
                            <span style={{ color: "#475569" }}>{l}</span>
                            <span style={{ color: "#94a3b8" }}>{v}</span>
                        </div>
                    ))}

                    <div style={{ display: "flex", gap: 16, marginTop: 14, fontSize: 12, color: "#475569" }}>
                        <span><b style={{ color: "#f1f5f9" }}>{connections.length}</b> Connected</span>
                        <span><b style={{ color: "#f59e0b" }}>{pending.length}</b> Requests</span>
                        <span><b style={{ color: "#8b5cf6" }}>{suggestions.length}</b> Suggested</span>
                    </div>

                    <Button size="small" icon={<EditOutlined />}
                        style={{ marginTop: 14, borderRadius: 6, borderColor: "rgba(99,102,241,0.4)", color: "#a78bfa" }}
                        onClick={() => { form.setFieldsValue({ fullName: user.fullName, phone: user.phone }); setPreview(user.profilePicture || null); setNewImg(null); setEditing(true); }}>
                        Edit Profile
                    </Button>
                </div>
            ),
        },
        {
            key: "find",
            label: "Find",
            children: (
                <div>
                    <div style={{ display: "flex", gap: 6, marginBottom: 12 }}>
                        <Input placeholder="Search by name or email"
                            value={query} onChange={e => setQuery(e.target.value)} onPressEnter={search}
                            style={{ ...inputStyle, flex: 1, height: 34 }}
                            prefix={<SearchOutlined style={{ color: "#475569" }} />} />
                        <Button size="small" loading={searching} onClick={search}
                            style={{ borderRadius: 6, background: "#6366f1", borderColor: "#6366f1", color: "#fff", height: 34 }}>
                            Go
                        </Button>
                    </div>
                    {results.length === 0
                        ? <Empty text={query ? "No users found" : "Search for someone"} />
                        : results.map(u => (
                            <Row key={u.email} name={u.name} email={u.email} pic={u.profilePic}
                                actions={sentMap[u.email]
                                    ? <span style={{ color: "#10b981", fontSize: 11 }}>✓ Sent</span>
                                    : <>
                                        <Select size="small" placeholder="Relation" style={{ width: 110 }}
                                            value={relMap[u.email] || undefined}
                                            onChange={v => setRelMap(p => ({ ...p, [u.email]: v }))}
                                            showSearch optionFilterProp="children" popupMatchSelectWidth={false}>
                                            {relations.map(r => (
                                                <Select.Option key={r.id} value={r.id}>
                                                    {r.relationName.charAt(0).toUpperCase() + r.relationName.slice(1).toLowerCase()}
                                                </Select.Option>
                                            ))}
                                        </Select>
                                        <button onClick={() => sendRequest(u.email)} disabled={sendingMap[u.email]}
                                            style={{ background: "#6366f1", border: "none", borderRadius: 5, color: "#fff", cursor: "pointer", width: 26, height: 26, fontSize: 13 }}>
                                            →
                                        </button>
                                    </>
                                }
                            />
                        ))
                    }
                </div>
            ),
        },
        {
            key: "connected",
            label: `Connected ${connections.length > 0 ? `(${connections.length})` : ""}`,
            children: (
                <Spin spinning={loading}>
                    {connections.length === 0 && !loading
                        ? <Empty text="No connections yet" />
                        : connections.map((c, i) => (
                            <Row key={i} name={c.suggestedUserName} email={c.suggestedUserEmail}
                                pic={c.suggestedUserProfilePic} rel={c.inferredRelation} />
                        ))
                    }
                </Spin>
            ),
        },
        {
            key: "requests",
            label: `Requests ${pending.length > 0 ? `(${pending.length})` : ""}`,
            children: (
                <Spin spinning={loading}>
                    {pending.length === 0 && !loading
                        ? <Empty text="No pending requests" />
                        : pending.map((p, i) => (
                            <Row key={i} name={p.suggestedUserName} pic={p.suggestedUserProfilePic}
                                rel={p.inferredRelation} reason={p.reason}
                                actions={<><Btn accept onClick={() => acceptPending(p.pendingRelationId)} /><Btn onClick={() => declinePending(p.pendingRelationId)} /></>}
                            />
                        ))
                    }
                </Spin>
            ),
        },
        {
            key: "suggested",
            label: `Suggested ${suggestions.length > 0 ? `(${suggestions.length})` : ""}`,
            children: (
                <Spin spinning={loading}>
                    {suggestions.length === 0 && !loading
                        ? <Empty text="No suggestions yet" />
                        : suggestions.map((s, i) => (
                            <Row key={i} name={s.suggestedUserName} pic={s.suggestedUserProfilePic}
                                rel={s.inferredRelation} reason={s.reason}
                                actions={<><Btn accept onClick={() => acceptSuggestion(s)} /><Btn onClick={() => dismissSuggestion(s)} /></>}
                            />
                        ))
                    }
                </Spin>
            ),
        },
    ];

    return (
        <Modal open={open} onCancel={() => { setEditing(false); onClose(); }}
            footer={null} width={550} centered closable
            title={
                <span style={{ color: "#a78bfa", fontSize: 14, fontWeight: 700 }}>
                    {editing ? "Edit Profile" : "Profile"}
                </span>
            }
            styles={{
                content: { background: "#0a0e1a", border: "1px solid rgba(99,102,241,0.18)", borderRadius: 12, padding: 0 },
                header:  { background: "#0a0e1a", borderBottom: "1px solid rgba(255,255,255,0.06)", padding: "12px 18px", margin: 0 },
                body:    { background: "#0a0e1a", color: "#f8fafc", padding: "14px 18px 18px" },
                mask:    { backgroundColor: "rgba(0,0,0,0.65)", backdropFilter: "blur(4px)" },
            }}
        >
            {!editing && (
                <Tabs items={tabItems} size="small"
                    tabBarStyle={{ borderBottom: "1px solid rgba(255,255,255,0.06)", marginBottom: 12, fontSize: 12 }} />
            )}

            {editing && (
                <Form form={form} layout="vertical" onFinish={save}>
                    <div style={{ textAlign: "center", marginBottom: 16 }}>
                        <ImgCrop rotationSlider aspect={1}>
                            <Upload showUploadList={false} customRequest={() => {}}
                                beforeUpload={file => {
                                    if (!file.type.startsWith("image/")) { message.error("Images only!"); return Upload.LIST_IGNORE; }
                                    if (file.size > 5 * 1024 * 1024)    { message.error("Max 5MB!");     return Upload.LIST_IGNORE; }
                                    const r = new FileReader();
                                    r.onload = e => { setPreview(e.target.result); setNewImg(e.target.result); };
                                    r.readAsDataURL(file); return false;
                                }}>
                                <div style={{ cursor: "pointer", position: "relative", display: "inline-block" }}>
                                    <Avatar size={64} src={preview || null} icon={!preview && <UserOutlined />}
                                        style={{ background: "#6366f1", color: "#fff" }} />
                                    <div style={{
                                        position: "absolute", bottom: 0, right: 0, background: "#6366f1",
                                        borderRadius: "50%", width: 20, height: 20,
                                        display: "flex", alignItems: "center", justifyContent: "center", border: "2px solid #0a0e1a",
                                    }}>
                                        <CameraOutlined style={{ color: "#fff", fontSize: 9 }} />
                                    </div>
                                </div>
                            </Upload>
                        </ImgCrop>
                        {preview && <Button type="link" danger size="small" style={{ display: "block", margin: "4px auto 0" }}
                            onClick={() => { setPreview(null); setNewImg(""); }}>Remove</Button>}
                    </div>

                    {[
                        { n: "fullName", l: "Full Name", icon: <UserOutlined />,  ph: "Full name", rules: [] },
                        { n: "phone",    l: "Phone",     icon: <PhoneOutlined />, ph: "10-digit phone",
                          rules: [{ pattern: /^[0-9]{10}$/, message: "10 digits" }] },
                    ].map(({ n, l, icon, ph, rules }) => (
                        <Form.Item key={n} name={n} rules={rules}
                            label={<span style={{ color: "#64748b", fontSize: 11 }}>{l}</span>}
                            style={{ marginBottom: 12 }}>
                            <Input prefix={<span style={{ color: "#6366f1" }}>{icon}</span>} placeholder={ph} style={inputStyle} />
                        </Form.Item>
                    ))}

                    <div style={{ borderTop: "1px solid rgba(255,255,255,0.06)", margin: "12px 0", paddingTop: 12 }}>
                        <div style={{ color: "#334155", fontSize: 10, letterSpacing: 1, marginBottom: 10 }}>CHANGE PASSWORD</div>
                        {[
                            { n: "currentPassword", l: "Current", ph: "Current password", rules: [] },
                            { n: "newPassword",     l: "New",     ph: "New password", rules: [{ min: 8, message: "Min 8 chars" }] },
                        ].map(({ n, l, ph, rules }) => (
                            <Form.Item key={n} name={n} rules={rules}
                                label={<span style={{ color: "#64748b", fontSize: 11 }}>{l}</span>}
                                style={{ marginBottom: 12 }}>
                                <Input.Password prefix={<LockOutlined style={{ color: "#6366f1" }} />} placeholder={ph} style={inputStyle} />
                            </Form.Item>
                        ))}
                    </div>

                    <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
                        <Button size="small" onClick={() => setEditing(false)}
                            style={{ borderRadius: 6, borderColor: "rgba(255,255,255,0.1)", color: "#64748b" }}>Cancel</Button>
                        <Button size="small" type="primary" htmlType="submit" loading={saving}
                            style={{ borderRadius: 6, background: "#6366f1", borderColor: "#6366f1" }}>Save</Button>
                    </div>
                </Form>
            )}
        </Modal>
    );
}

export default UserProfile;