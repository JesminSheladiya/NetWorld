import {
    Table,
    Button,
    Modal,
    Form,
    Input,
    Popconfirm,
    Space,
    Typography,
    Card,
    Layout,
    ConfigProvider,
    theme,
    Select,
    message,
    Spin,
    Tag,
    Badge,
    Tooltip,
    Row,
    Col,
    Avatar,
    Upload,
    Tabs,
} from "antd";
import { EditOutlined, DeleteOutlined, CheckCircleOutlined, CloseCircleOutlined, ArrowRightOutlined, SearchOutlined, BulbOutlined, TeamOutlined } from "@ant-design/icons";
import { useState, useEffect } from "react";
import ImgCrop from 'antd-img-crop';
import { http } from "../Services/https";
import "./css/ContactsTable.css";

const { Content } = Layout;

function ContactsTable() {
    const [dataSource, setDataSource] = useState([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingRecord, setEditingRecord] = useState(null);
    const [relations, setRelations] = useState([]);
    const [total, setTotal] = useState([]);
    const [form] = Form.useForm();

    const [showSuggestions, setShowSuggestions] = useState(false);

    const [searchText, setSearchText] = useState("");
    const [messageApi, contextHolder] = message.useMessage();
    const [loading, setLoading] = useState(false);
    const [imageModalVisible, setImageModalVisible] = useState(false);
    const [selectedImage, setSelectedImage] = useState(null);

    const [uploadedImage, setUploadedImage] = useState(null);
    const [imageFile, setImageFile] = useState(null);

    const BASE = process.env.REACT_APP_API_URL?.replace("/api/contacts", "/api") || "http://localhost:8080/api";
    const API_URL = process.env.REACT_APP_API_URL || "http://localhost:8080/api/contacts";

    const [query, setQuery] = useState("");
    const [searchResults, setSearchResults] = useState([]);
    const [searching, setSearching] = useState(false);
    const [relMap, setRelMap] = useState({});
    const [sendingMap, setSendingMap] = useState({});
    const [sentMap, setSentMap] = useState({});

    const [userSuggestions, setUserSuggestions] = useState([]);
    const [userSuggestLoading, setUserSuggestLoading] = useState(false);

    useEffect(() => {
        const token = localStorage.getItem("token");
        if (!token) {
            messageApi.error("Please login first!");
            return;
        }
        fetchRelations();
    }, []);

    useEffect(() => {
        if (relations.length > 0) {
            fetchContacts();
        }
    }, [relations]);


    const fetchContacts = async () => {
        setLoading(true);
        try {

            const response = await http.get(`${API_URL}`);
            const contactsWithRelations = response.data.map(item => {
                const relationObj = relations.find(r => r.id == item.relationId);
                return {
                    key: item.id,
                    ...item,
                    relation: relationObj || {
                        id: item.relationId,
                        relationName: `Relation ${item.relationId}`
                    }
                };
            });

            setDataSource(contactsWithRelations);

        } catch (error) {
            messageApi.error("Failed to load contacts!");
            console.error(error);
            if (error.response?.status === 403 || error.response?.status === 401) {
                messageApi.error("Session expired. Please login again!");
                localStorage.clear();
            }
        } finally {
            setLoading(false);
        }
    };

    const fetchRelations = async () => {
        try {
            const response = await http.get(`${API_URL}/relations`);
            setRelations(response.data);
        } catch (error) {
            messageApi.error("Failed to load relations!");
            console.error(error);
        }
    };

    const searchUsers = async () => {
        if (!query.trim()) return;
        setSearching(true);
        setSearchResults([]);
        try {
            const res = await http.get(`${BASE}/user-relations/search-users?query=${encodeURIComponent(query)}`);
            setSearchResults(res.data);
        } catch { messageApi.error("Search failed!"); }
        finally { setSearching(false); }
    };

    const sendUserRequest = async (email) => {
        if (!relMap[email]) { messageApi.warning("Select a relation first!"); return; }
        setSendingMap(p => ({ ...p, [email]: true }));
        try {
            await http.post(`${BASE}/user-relations/send`, { toEmail: email, relationId: relMap[email] });
            setSentMap(p => ({ ...p, [email]: true }));
            messageApi.success("Request sent!");
        } catch (e) { messageApi.error(e.response?.data?.message || "Failed!"); }
        finally { setSendingMap(p => ({ ...p, [email]: false })); }
    };

    const fetchUserSuggestions = async () => {
        setUserSuggestLoading(true);
        try {
            const res = await http.get(`${BASE}/user-relations/suggestions`);
            setUserSuggestions(res.data);
        } catch { messageApi.error("Failed to load suggestions!"); }
        finally { setUserSuggestLoading(false); }
    };

    const acceptUserSuggestion = async (s) => {
        try {
            await http.post(`${BASE}/user-relations/suggestions/accept`, {
                otherEmail: s.suggestedUserEmail, relationName: s.inferredRelation,
            });
            setUserSuggestions(p => p.filter(x => x.suggestedUserEmail !== s.suggestedUserEmail));
            messageApi.success("Suggestion accepted!");
        } catch { messageApi.error("Failed!"); }
    };

    const dismissUserSuggestion = async (s) => {
        try {
            await http.delete(`${BASE}/user-relations/suggestions/${s.pendingRelationId}/dismiss`);
            setUserSuggestions(p => p.filter(x => x.suggestedUserEmail !== s.suggestedUserEmail));
        } catch { messageApi.error("Failed to dismiss!"); }
    };

    const handleAdd = () => {
        setEditingRecord(null);
        form.resetFields();

        setUploadedImage(null);
        setImageFile(null);
        setIsModalOpen(true);
    };

    const handleEdit = (record) => {
        const formValues = {
            name: record.name,
            phone: record.phone,
            email: record.email,
            relationId: record.relationId || record.relation?.id
        };

        setEditingRecord(record);
        form.setFieldsValue(formValues);

        setUploadedImage(record.profilePicture || null);
        setImageFile(null);
        setIsModalOpen(true);
    };


    const handleDelete = async (id) => {
        setLoading(true);
        try {
            await http.delete(`${API_URL}/${id}`);
            setDataSource(dataSource.filter((item) => item.key !== id));
            messageApi.success("Contact deleted successfully!");
        } catch (error) {
            messageApi.error("Failed to delete contact!");
            console.error(error);
        } finally {
            setLoading(false);
        }
    };

    const handleImageUpload = async (file, contactId) => {
        if (!file) return;

        if (!file.type.startsWith("image/")) {
            messageApi.error("Only image files allowed!");
            return;
        }

        if (file.size > 5 * 1024 * 1024) {
            messageApi.error("Image size must be less than 5MB!");
            return;
        }

        const formData = new FormData();
        formData.append("file", file);

        try {
            const response = await http.post(
                `${API_URL}/${contactId}/upload-picture`,
                formData,
                {
                    headers: { "Content-Type": "multipart/form-data" },
                }
            );

            setDataSource((prev) =>
                prev.map((item) =>
                    item.id === contactId
                        ? { ...item, profilePicture: response.data.profilePicture }
                        : item
                )
            );
            messageApi.success("Profile picture updated!");
        } catch (error) {
            messageApi.error("Upload failed!");
            console.error(error);
        }
    };

    const handleRemoveImage = async (contactId) => {
        try {
            const response = await http.delete(`${API_URL}/${contactId}/remove-picture`);


            setDataSource((prev) =>
                prev.map((item) =>
                    item.id === contactId
                        ? { ...item, profilePicture: null }
                        : item
                )
            );
            messageApi.success("Profile picture removed!");
        } catch (error) {
            messageApi.error("Remove failed!");
            console.error(error);
        }
    };

    const handleOk = () => {
        form
            .validateFields()
            .then(async (values) => {
                setIsModalOpen(false);
                setLoading(true);

                try {
                    const payload = {
                        ...values,
                        phone: editingRecord ? editingRecord.phone : values.phone,
                        profilePicture: uploadedImage !== undefined ? uploadedImage : null
                    };

                    let response;
                    let savedContact;

                    if (editingRecord) {
                        response = await http.put(`${API_URL}/${editingRecord.id}`, payload);
                        savedContact = response.data;
                    } else {
                        response = await http.post(API_URL, payload);
                        savedContact = response.data;
                    }

                    const relationObj = relations.find(r => r.id == savedContact.relationId);
                    const contactWithRelation = {
                        ...savedContact,
                        key: savedContact.id,
                        relation: relationObj || {
                            id: savedContact.relationId,
                            relationName: `Relation ${savedContact.relationId}`
                        }
                    };

                    if (editingRecord) {
                        setDataSource((prev) =>
                            prev.map((item) =>
                                item.id === editingRecord.id ? contactWithRelation : item
                            )
                        );
                        messageApi.success("Contact updated successfully!");
                    } else {
                        setDataSource((prev) => [...prev, contactWithRelation]);
                        messageApi.success("Contact added successfully!");
                    }

                    form.resetFields();
                    setUploadedImage(null);
                    setImageFile(null);

                } catch (error) {
                    console.error("Full error:", error);
                    const backendMsg = error.response?.data?.message
                        || error.response?.data?.errors?.join(", ")
                        || error.response?.data
                        || "Something went wrong!";

                    if (error.response?.status === 400) {
                        messageApi.error(`Validation Error: ${JSON.stringify(backendMsg)}`);
                    } else if (error.response?.status === 409) {
                        messageApi.error("Phone number already exists!");
                    } else if (error.response?.status === 401 || error.response?.status === 403) {
                        messageApi.error("Session expired. Please login again!");
                        localStorage.clear();
                    } else {
                        messageApi.error(`Error: ${backendMsg}`);
                    }
                    setIsModalOpen(true);
                } finally {
                    setLoading(false);
                }
            })
            .catch(() => {
                messageApi.error("Please fill all required fields!");
            });
    };


    const filteredData = dataSource.filter((item) => {
        const text = searchText.toLowerCase();

        const relationName = item.relation
            ? (typeof item.relation === 'string'
                ? item.relation
                : (item.relation.relationName || item.relation || ''))
            : '';

        return (
            (item.name || '').toLowerCase().includes(text) ||
            (item.phone || '').toLowerCase().includes(text) ||
            (item.email || '').toLowerCase().includes(text) ||
            relationName.toLowerCase().includes(text)
        );
    });


    const columns = [
        {
            title: "Photo",
            dataIndex: "profilePicture",
            key: "profilePicture",
            width: 70,
            render: (pic, record) => (
                <div style={{ display: "flex", justifyContent: "center" }}>
                    <div
                        style={{ cursor: pic ? "pointer" : "default" }}
                        onClick={() => {
                            if (pic) {
                                setSelectedImage(pic);
                                setImageModalVisible(true);
                            }
                        }}
                    >
                        <Tooltip title={pic ? "Click to view full image" : ""}>
                            <Avatar
                                size={45}
                                src={pic || null}
                                style={{
                                    backgroundColor: pic ? "transparent" : "#3b82f6",
                                    fontSize: "18px",
                                }}
                            >
                                {!pic && record.name?.charAt(0).toUpperCase()}
                            </Avatar>
                        </Tooltip>
                    </div>
                </div>
            ),
        },
        {
            title: "Name",
            dataIndex: "name",
            key: "name",
            sorter: (a, b) => a.name.localeCompare(b.name),
        },
        {
            title: "Phone Number",
            dataIndex: "phone",
            key: "phone",
            sorter: (a, b) => a.phone.localeCompare(b.phone),
        },
        {
            title: "Email",
            dataIndex: "email",
            key: "email",
            sorter: (a, b) => a.email.localeCompare(b.email),
        },
        {
            title: "Relation",
            dataIndex: "relation",
            key: "relation",
            filters: relations.map((r) => ({
                text: typeof r === 'string'
                    ? r
                    : (r.relationName || r || 'Unknown'),
                value: r.id || r
            })),
            filterSearch: true,
            onFilter: (value, record) => record.relationId === value,
            render: (relation) => {
                if (!relation) return <span style={{ color: '#334155' }}>—</span>;
                const name = typeof relation === 'string'
                    ? relation
                    : (relation.relationName || relation || `Relation ${relation.id}`);
                const label = name.charAt(0).toUpperCase() + name.slice(1).toLowerCase();
                const r = name.toLowerCase();
                let color = '#22d3ee', bg = 'rgba(34,211,238,0.1)', border = 'rgba(34,211,238,0.25)';
                if (r.includes('brother') || r.includes('sister')) { color = '#60a5fa'; bg = 'rgba(96,165,250,0.1)'; border = 'rgba(96,165,250,0.25)'; }
                else if (r.includes('father') || r.includes('mother')) { color = '#a78bfa'; bg = 'rgba(167,139,250,0.1)'; border = 'rgba(167,139,250,0.25)'; }
                else if (r.includes('son') || r.includes('daughter')) { color = '#34d399'; bg = 'rgba(52,211,153,0.1)'; border = 'rgba(52,211,153,0.25)'; }
                else if (r.includes('grand')) { color = '#fbbf24'; bg = 'rgba(251,191,36,0.1)'; border = 'rgba(251,191,36,0.25)'; }
                else if (r.includes('husband') || r.includes('wife')) { color = '#f472b6'; bg = 'rgba(244,114,182,0.1)'; border = 'rgba(244,114,182,0.25)'; }
                else if (r.includes('friend')) { color = '#f59e0b'; bg = 'rgba(245,158,11,0.1)'; border = 'rgba(245,158,11,0.25)'; }
                else if (r.includes('uncle') || r.includes('aunt')) { color = '#fb923c'; bg = 'rgba(251,146,60,0.1)'; border = 'rgba(251,146,60,0.25)'; }
                return (
                    <span style={{
                        display: 'inline-flex', alignItems: 'center', gap: 5,
                        color, background: bg,
                        border: `1px solid ${border}`,
                        borderRadius: 20, padding: '3px 10px',
                        fontSize: 12, fontWeight: 600,
                        letterSpacing: 0.3, whiteSpace: 'nowrap',
                    }}>
                        {label}
                    </span>
                );
            },
        },


        {
            title: "Actions",
            key: "actions",
            render: (_, record) => (
                <Space>
                    <Tooltip title="Edit contact">
                        <Button
                            type="text"
                            size="middle"
                            icon={<EditOutlined />}
                            className="action-edit"
                            onClick={() => handleEdit(record)}
                        >
                            Edit
                        </Button>
                    </Tooltip>
                    <Popconfirm
                        title="Are you sure you want to delete this record?"
                        okText="Yes"
                        cancelText="No"
                        onConfirm={() => handleDelete(record.key)}
                    >
                        <Button
                            type="text"
                            size="middle"
                            icon={<DeleteOutlined />}
                            className="action-delete"
                        >
                            Delete
                        </Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    return (
        <ConfigProvider theme={{ algorithm: theme.darkAlgorithm }}>
            {contextHolder}
            <Layout className="contacts-layout">
                <Content className="contacts-content">
                    <Card className="contacts-card">
                        <Space
                            className="contacts-header-row"
                            align="center"
                            style={{
                                marginBottom: 24,
                                width: "100%",
                                justifyContent: "space-between",
                            }}
                        >
                            <Typography.Title
                                level={3}
                                style={{ color: "#f1f5f9", margin: 0 }}
                            >
                                Net World
                            </Typography.Title>

                            <Space>
                                <Input.Search
                                    placeholder="Search contacts..."
                                    allowClear
                                    value={searchText}
                                    onChange={(e) => setSearchText(e.target.value)}
                                    style={{ width: 250 }}
                                />
                                <Button type="primary" size="middle" onClick={handleAdd}>
                                    Add Details
                                </Button>
                                <Button
                                    type="primary"
                                    size="middle"
                                    onClick={() => { fetchUserSuggestions(); setShowSuggestions(true); }}
                                >
                                    Relation Suggestions
                                </Button>

                            </Space>
                        </Space>

                        <Spin spinning={loading} tip="Processing..." size="large" className="spin-container">
                            <Table
                                bordered
                                columns={columns}
                                dataSource={filteredData}
                                pagination={{ pageSize: 5 }}
                                className="contacts-table"
                                size="middle"
                            />
{showSuggestions && (
    <div style={{
        marginTop: 24,
        background: "#0a0e1a",
        border: "1px solid rgba(99, 102, 241, 0.2)",
        borderRadius: 12,
        overflow: "hidden"
    }}>
        {/* Panel Header Structure */}
        <Row justify="space-between" align="middle" style={{ padding: "16px 20px", borderBottom: "1px solid rgba(255, 255, 255, 0.06)" }}>
            <Col>
                <Space size={12}>
                    <Avatar 
                        icon={<TeamOutlined />} 
                        style={{ backgroundColor: "rgba(99, 102, 241, 0.15)", color: "#818cf8", border: "1px solid rgba(99, 102, 241, 0.2)" }} 
                    />
                    <div>
                        <Typography.Text strong style={{ color: "#a78bfa", fontSize: 15, display: "block", letterSpacing: 0.3 }}>
                            Relation Discovery Hub
                        </Typography.Text>
                        <Typography.Text style={{ color: "#475569", fontSize: 11 }}>
                            Find users · Get automated suggestions · Discover network connections
                        </Typography.Text>
                    </div>
                </Space>
            </Col>
            <Col>
                <Button 
                    type="text" 
                    danger 
                    onClick={() => setShowSuggestions(false)}
                    style={{ fontSize: 12, fontWeight: 600 }}
                >
                    Close Hub
                </Button>
            </Col>
        </Row>

        {/* Unified Discovery Tabs System */}
        <Tabs
            size="small"
            defaultActiveKey="suggested"
            tabBarStyle={{ padding: "0 20px", marginBottom: 0, borderBottom: "1px solid rgba(255, 255, 255, 0.06)" }}
            items={[
                /* ── TAB 1: FIND PEOPLE ── */
                {
                    key: "find",
                    label: (
                        <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
                            <SearchOutlined style={{ fontSize: 12 }} /> Find People
                        </span>
                    ),
                    children: (
                        <div style={{ padding: 20 }}>
                            <Row gap={12} style={{ marginBottom: 16, display: "flex" }}>
                                <Col style={{ flex: 1, marginRight: 10 }}>
                                    <Input
                                        placeholder="Search by name or email..."
                                        value={query}
                                        onChange={e => setQuery(e.target.value)}
                                        onPressEnter={searchUsers}
                                        prefix={<SearchOutlined style={{ color: "#64748b" }} />}
                                        style={{ background: "#0d1117", borderColor: "rgba(99,102,241,0.3)", color: "#f8fafc", height: 38 }}
                                    />
                                </Col>
                                <Col>
                                    <Button type="primary" loading={searching} onClick={searchUsers} style={{ height: 38, background: "linear-gradient(135deg,#6366f1,#8b5cf6)", border: "none" }}>
                                        Search
                                    </Button>
                                </Col>
                            </Row>

                            {searchResults.length === 0 ? (
                                <div style={{ textAlign: "center", padding: "32px 0", color: "#475569" }}>
                                    <div style={{ fontSize: 24, marginBottom: 8 }}>{query ? "🔍" : "👥"}</div>
                                    <div style={{ fontSize: 12 }}>{query ? "No users found matching your search" : "Search for someone to connect with"}</div>
                                </div>
                            ) : (
                                <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                                    <div style={{ fontSize: 10, fontWeight: 700, color: "#334155", letterSpacing: 1.2, marginBottom: 4 }}>RESULTS ({searchResults.length})</div>
                                    {searchResults.map(u => (
                                        <Row key={u.email} justify="space-between" align="middle" style={{ padding: "10px 14px", background: "rgba(255,255,255,0.02)", border: "1px solid rgba(255,255,255,0.05)", borderRadius: 8 }}>
                                            <Col>
                                                <Space size={12}>
                                                    <Avatar style={{ backgroundColor: "#3b82f6", fontWeight: 600 }}>{(u.name || "?").charAt(0).toUpperCase()}</Avatar>
                                                    <div>
                                                        <div style={{ color: "#e2e8f0", fontSize: 13, fontWeight: 600 }}>{u.name}</div>
                                                        <div style={{ color: "#64748b", fontSize: 11 }}>{u.email}</div>
                                                    </div>
                                                </Space>
                                            </Col>
                                            <Col>
                                                {sentMap[u.email] ? (
                                                    <Tag color="success" style={{ borderRadius: 4, margin: 0 }}>✓ Sent</Tag>
                                                ) : (
                                                    <Space size={8}>
                                                        <Select
                                                            size="small"
                                                            placeholder="Relation"
                                                            style={{ width: 120 }}
                                                            value={relMap[u.email] || undefined}
                                                            onChange={v => setRelMap(p => ({ ...p, [u.email]: v }))}
                                                        >
                                                            {relations.map(r => (
                                                                <Select.Option key={r.id} value={r.id}>
                                                                    {r.relationName.charAt(0).toUpperCase() + r.relationName.slice(1).toLowerCase()}
                                                                </Select.Option>
                                                            ))}
                                                        </Select>
                                                        <Button 
                                                            size="small"
                                                            type="primary"
                                                            icon={<ArrowRightOutlined style={{ fontSize: 11 }} />}
                                                            loading={sendingMap[u.email]}
                                                            onClick={() => sendUserRequest(u.email)}
                                                            style={{ background: "rgba(99,102,241,0.15)", color: "#818cf8", borderColor: "rgba(99,102,241,0.3)" }}
                                                        />
                                                    </Space>
                                                )}
                                            </Col>
                                        </Row>
                                    ))}
                                </div>
                            )}
                        </div>
                    )
                },

                /* ── TAB 2: SUGGESTIONS (PEOPLE YOU MAY KNOW) ── */
                {
    key: "suggested",
    label: (
        <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <BulbOutlined style={{ fontSize: 12 }} /> Suggestions 
            {userSuggestions.length > 0 && (
                <span style={{ background: "#6366f1", color: "#fff", borderRadius: 10, padding: "0 5px", fontSize: 10, fontWeight: 700 }}>{userSuggestions.length}</span>
            )}
        </span>
    ),
    children: (
        <div style={{ padding: "20px", width: "100%", display: "block", textAlign: "left" }}>
            {/* Header section with forced left alignment */}
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, width: "100%", textAlign: "left" }}>
                <div style={{ display: "block", textAlign: "left" }}>
                    <div style={{ fontSize: 10, fontWeight: 700, color: "#334155", letterSpacing: "1.2px", textTransform: "uppercase", textAlign: "left", display: "block" }}>
                        People You May Know
                    </div>
                    <div style={{ color: "#475569", fontSize: 11, textAlign: "left", display: "block", marginTop: 2 }}>
                        Auto-discovered through mutual connections
                    </div>
                </div>
                <div>
                    <Button size="small" type="dashed" loading={userSuggestLoading} onClick={fetchUserSuggestions} style={{ borderColor: "rgba(99,102,241,0.3)", color: "#a78bfa", background: "transparent", fontSize: 11 }}>
                        Refresh Suggestions
                    </Button>
                </div>
            </div>

            {userSuggestLoading ? (
                <div style={{ textAlign: "center", padding: "20px 0", color: "#64748b" }}><Spin size="small" style={{ marginRight: 8 }} /> Loading...</div>
            ) : userSuggestions.length === 0 ? (
                <div style={{ textAlign: "center", padding: "32px 0", color: "#475569" }}>
                    <div style={{ fontSize: 24, marginBottom: 8 }}>✨</div>
                    <div style={{ fontSize: 12 }}>No suggestions yet</div>
                </div>
            ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: 10, width: "100%", textAlign: "left" }}>
                    {userSuggestions.map((s, i) => {
                        const rel = (s.inferredRelation || "").toLowerCase();
                        let rColor = "#22d3ee", rBg = "rgba(34,211,238,0.1)", rBorder = "rgba(34,211,238,0.25)";
                        if (rel.includes("brother") || rel.includes("sister")) { rColor = "#60a5fa"; rBg = "rgba(96,165,250,0.1)"; rBorder = "rgba(96,165,250,0.25)"; }
                        else if (rel.includes("father") || rel.includes("mother")) { rColor = "#a78bfa"; rBg = "rgba(167,139,250,0.1)"; rBorder = "rgba(167,139,250,0.25)"; }
                        else if (rel.includes("son") || rel.includes("daughter")) { rColor = "#34d399"; rBg = "rgba(52,211,153,0.1)"; rBorder = "rgba(52,211,153,0.25)"; }

                        const COLORS = ["#6366f1", "#0ea5e9", "#10b981", "#f59e0b", "#8b5cf6"];
                        const avColor = COLORS[((s.suggestedUserName || "").charCodeAt(0) || 0) % COLORS.length];

                        return (
                            <div 
                                key={i} 
                                style={{ 
                                    display: "flex", 
                                    justifyContent: "space-between", 
                                    alignItems: "center", 
                                    padding: "14px 16px", 
                                    background: "rgba(255,255,255,0.02)", 
                                    border: "1px solid rgba(255,255,255,0.05)", 
                                    borderRadius: 10,
                                    width: "100%",
                                    boxSizing: "border-box"
                                }}
                            >
                                {/* Left Profile Group */}
                                <div style={{ display: "flex", alignItems: "center", gap: 14, flex: 1, minWidth: 0, textAlign: "left" }}>
                                    <Avatar size={42} style={{ backgroundColor: avColor, fontWeight: 700, border: "2px solid rgba(255,255,255,0.08)", flexShrink: 0 }}>
                                        {(s.suggestedUserName || "?").charAt(0).toUpperCase()}
                                    </Avatar>
                                    
                                    <div style={{ display: "flex", flexDirection: "column", gap: 2, minWidth: 0, width: "100%", textAlign: "left" }}>
                                        <div style={{ color: "#e2e8f0", fontWeight: 600, fontSize: 14, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", textAlign: "left" }}>
                                            {s.suggestedUserName}
                                        </div>
                                        <div style={{ color: "#64748b", fontSize: 12, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", textAlign: "left" }}>
                                            {s.suggestedUserEmail}
                                        </div>
                                        <div style={{ display: "flex", alignItems: "center", gap: 6, marginTop: 4, flexWrap: "wrap", justifyContent: "flex-start", textAlign: "left" }}>
                                            <span style={{ background: "rgba(99,102,241,0.15)", color: "#818cf8", fontSize: 9, fontWeight: 700, padding: "1px 5px", borderRadius: 4, flexShrink: 0 }}>AUTO</span>
                                            <span style={{ color: "#475569", fontSize: 11, fontStyle: "italic", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", textAlign: "left" }}>
                                                {s.reason}
                                            </span>
                                        </div>
                                    </div>
                                </div>

                                {/* Right Badges & Controls */}
                                <div style={{ display: "flex", alignItems: "center", gap: 12, flexShrink: 0, marginLeft: 16 }}>
                                    <span style={{ color: rColor, background: rBg, border: `1px solid ${rBorder}`, borderRadius: 20, padding: "3px 12px", fontSize: 11, fontWeight: 600, letterSpacing: 0.3, whiteSpace: "nowrap" }}>
                                        {(s.inferredRelation || "").toUpperCase()}
                                    </span>
                                    <Space size={6}>
                                        <Tooltip title="Accept Connection">
                                            <button onClick={() => acceptUserSuggestion(s)} style={{ width: 30, height: 30, borderRadius: 6, cursor: "pointer", border: "none", background: "rgba(16,185,129,0.15)", color: "#10b981", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: "bold" }}>✓</button>
                                        </Tooltip>
                                        <Tooltip title="Dismiss">
                                            <button onClick={() => dismissUserSuggestion(s)} style={{ width: 30, height: 30, borderRadius: 6, cursor: "pointer", border: "none", background: "rgba(239,68,68,0.12)", color: "#ef4444", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: "bold" }}>✕</button>
                                        </Tooltip>
                                    </Space>
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    )
},

            ]}
        />
    </div>
)}

                            {/* Image Viewer Modal - WhatsApp/Instagram DP Style */}
                            <Modal
                                title={null}
                                open={imageModalVisible}
                                onCancel={() => setImageModalVisible(false)}
                                destroyOnClose
                                maskClosable={true}
                                footer={null}
                                closable={false}
                                centered
                                className="image-viewer-modal-circle"
                            >
                                {selectedImage && (
                                    <div style={{
                                        display: 'flex',
                                        justifyContent: 'center',
                                        alignItems: 'center',
                                    }}>
                                        <img
                                            src={selectedImage}
                                            alt="Profile full view"
                                            style={{
                                                objectFit: 'cover',
                                                borderRadius: '50%',
                                                boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
                                                border: '4px solid rgba(255, 255, 255, 0.1)'
                                            }}
                                            onClick={() => setImageModalVisible(false)}
                                        />
                                    </div>
                                )}
                            </Modal>

                        </Spin>
                    </Card>

                    <Modal
                        title={editingRecord ? "Edit Contact" : "Add Contact"}
                        open={isModalOpen}
                        onOk={handleOk}
                        destroyOnClose
                        maskClosable={true}
                        onCancel={() => setIsModalOpen(false)}
                        okText="Save"
                        cancelText="Cancel"
                        className="contacts-modal"
                    >
                        <Form form={form} layout="vertical" className="contacts-form">
                            <Form.Item label="Profile Picture">
                                <div className="profile-upload-wrapper">
                                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '5px' }}>
                                        <ImgCrop rotationSlider aspect={1} showGrid>
                                            <Upload
                                                name="avatar"
                                                showUploadList={false}
                                                customRequest={() => { }}
                                                beforeUpload={(file) => {
                                                    if (!file.type.startsWith("image/")) {
                                                        messageApi.error("Only image files allowed!");
                                                        return Upload.LIST_IGNORE;
                                                    }
                                                    if (file.size > 5 * 1024 * 1024) {
                                                        messageApi.error("Image must be less than 5MB!");
                                                        return Upload.LIST_IGNORE;
                                                    }
                                                    setImageFile(file);
                                                    const reader = new FileReader();
                                                    reader.onload = (ev) => setUploadedImage(ev.target.result);
                                                    reader.readAsDataURL(file);
                                                    return false;
                                                }}
                                            >
                                                <div className="profile-avatar-label" style={{ cursor: "pointer", display: "inline-block", position: "relative" }}>
                                                    <Avatar
                                                        size={80}
                                                        src={uploadedImage || null}
                                                        style={{
                                                            backgroundColor: uploadedImage ? "transparent" : "#3b82f6",
                                                            fontSize: "28px",
                                                        }}
                                                    >
                                                        {!uploadedImage && (form.getFieldValue("name")?.charAt(0).toUpperCase() || "?")}
                                                    </Avatar>
                                                    <div className="profile-avatar-edit-icon">
                                                        <EditOutlined />
                                                    </div>
                                                </div>
                                            </Upload>
                                        </ImgCrop>
                                        {uploadedImage && (
                                            <Button
                                                type="link"
                                                danger
                                                size="small"
                                                onClick={async () => {
                                                    if (editingRecord) {
                                                        await handleRemoveImage(editingRecord.id);
                                                    }
                                                    setUploadedImage(null);
                                                    setImageFile(null);
                                                }}
                                            >
                                                Remove Profile Image
                                            </Button>
                                        )}
                                    </div>
                                </div>
                            </Form.Item>

                            <Form.Item
                                name="name"
                                label="Name"
                                rules={[
                                    { required: true, message: "Please input name!" },
                                    {
                                        pattern: /^[A-Za-z\s]+$/,
                                        message: "Name can only contain letters and spaces!",
                                    },
                                ]}
                            >
                                <Input
                                    placeholder="Enter full name"
                                    size="large"
                                    onKeyPress={(e) => {
                                        if (!/^[A-Za-z\s]$/.test(e.key)) {
                                            e.preventDefault();
                                        }
                                    }}
                                />
                            </Form.Item>

                            <Form.Item
                                name="phone"
                                label="Phone Number"
                                rules={[
                                    { required: true, message: "Please input phone!" },
                                    {
                                        pattern: /^[0-9]{10}$/,
                                        message: "Phone number must be exactly 10 digits!",
                                    },
                                ]}
                            >
                                <Input
                                    placeholder="Enter phone number"
                                    size="large"
                                    maxLength={10}
                                    disabled={!!editingRecord}
                                    onKeyPress={(e) => {
                                        if (!/[0-9]/.test(e.key)) {
                                            e.preventDefault();
                                        }
                                    }}
                                />
                            </Form.Item>

                            <Form.Item
                                name="email"
                                label="Email"
                                rules={[
                                    { required: true, message: "Please enter email!" },
                                    { type: "email", message: "Please enter valid email!" },
                                ]}
                            >
                                <Input placeholder="Enter email address" size="large" />
                            </Form.Item>

                            <Form.Item
                                name="relationId"
                                label="Relation"
                                rules={[{ required: true, message: "Please select relation!" }]}
                            >
                                <Select
                                    placeholder="Select relation"
                                    size="large"
                                    showSearch
                                    optionFilterProp="children"
                                    filterOption={(input, option) =>
                                        (option?.children ?? "").toLowerCase().includes(input.toLowerCase())
                                    }
                                    style={{ width: "100%" }}
                                >
                                    {relations.map((relation) => (
                                        <Select.Option
                                            key={relation.id}
                                            value={relation.id}
                                        >
                                            {typeof relation === 'string'
                                                ? relation.charAt(0).toUpperCase() + relation.slice(1).toLowerCase()
                                                : (relation.relationName || relation).charAt(0).toUpperCase() + (relation.relationName || relation).slice(1).toLowerCase()
                                            }
                                        </Select.Option>
                                    ))}
                                </Select>
                            </Form.Item>
                        </Form>
                    </Modal>
                </Content>
            </Layout>
        </ConfigProvider>
    );
}

export default ContactsTable;