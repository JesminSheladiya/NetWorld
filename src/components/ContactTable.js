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
} from "antd";
import { EditOutlined, DeleteOutlined, CheckCircleOutlined, CloseCircleOutlined, ArrowRightOutlined } from "@ant-design/icons";
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

    const [suggestions, setSuggestions] = useState([]);
    const [showSuggestions, setShowSuggestions] = useState(false);
    const [suggestLoading, setSuggestLoading] = useState(false);

    const [searchText, setSearchText] = useState("");
    const [messageApi, contextHolder] = message.useMessage();
    const [loading, setLoading] = useState(false);
    const [acceptedSuggestions, setAcceptedSuggestions] = useState([]);
    const [rejectedSuggestions, setRejectedSuggestions] = useState([]);
    const [imageModalVisible, setImageModalVisible] = useState(false);
    const [selectedImage, setSelectedImage] = useState(null);

    const [uploadedImage, setUploadedImage] = useState(null);
    const [imageFile, setImageFile] = useState(null);

    const API_URL = process.env.REACT_APP_API_URL || "http://localhost:8080/api/contacts";

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

    const fetchInferredRelations = async () => {
        setSuggestLoading(true);
        try {
            const response = await http.get(`${API_URL}/inferred-relations`);
            setSuggestions(response.data);
            setShowSuggestions(true);

            if (response.data.length === 0) {
                messageApi.info("No inferred relations found!");
            }
        } catch (error) {
            messageApi.error("Failed to fetch suggestions!");
        } finally {
            setSuggestLoading(false);
        }
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
                if (r.includes('brother') || r.includes('sister'))   { color = '#60a5fa'; bg = 'rgba(96,165,250,0.1)';   border = 'rgba(96,165,250,0.25)';   }
                else if (r.includes('father') || r.includes('mother')) { color = '#a78bfa'; bg = 'rgba(167,139,250,0.1)';  border = 'rgba(167,139,250,0.25)';  }
                else if (r.includes('son') || r.includes('daughter')) { color = '#34d399'; bg = 'rgba(52,211,153,0.1)';   border = 'rgba(52,211,153,0.25)';   }
                else if (r.includes('grand'))                         { color = '#fbbf24'; bg = 'rgba(251,191,36,0.1)';   border = 'rgba(251,191,36,0.25)';   }
                else if (r.includes('husband') || r.includes('wife')) { color = '#f472b6'; bg = 'rgba(244,114,182,0.1)';  border = 'rgba(244,114,182,0.25)';  }
                else if (r.includes('friend'))                        { color = '#f59e0b'; bg = 'rgba(245,158,11,0.1)';   border = 'rgba(245,158,11,0.25)';   }
                else if (r.includes('uncle') || r.includes('aunt'))   { color = '#fb923c'; bg = 'rgba(251,146,60,0.1)';   border = 'rgba(251,146,60,0.25)';   }
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
                                    onClick={fetchInferredRelations}
                                    loading={suggestLoading}
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
                                <div className="suggestions-panel">
                                    {/* Panel Header */}
                                    <div className="suggestions-panel-header">
                                        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                            <div className="suggestions-icon-wrap">
                                                <span style={{ fontSize: 18 }}>✨</span>
                                            </div>
                                            <div>
                                                <div style={{ color: '#f1f5f9', fontWeight: 700, fontSize: 15 }}>Intelligent Relation Suggestions</div>
                                                <div style={{ color: '#475569', fontSize: 12, marginTop: 2 }}>
                                                    {suggestions.length} suggestion{suggestions.length !== 1 ? 's' : ''} found
                                                </div>
                                            </div>
                                        </div>
                                        <Button
                                            type="text"
                                            size="small"
                                            onClick={() => setShowSuggestions(false)}
                                            style={{
                                                color: '#475569', borderRadius: 8,
                                                border: '1px solid rgba(255,255,255,0.08)',
                                                background: 'rgba(255,255,255,0.03)',
                                            }}
                                        >
                                            ✕ Close
                                        </Button>
                                    </div>

                                    {/* Suggestions List */}
                                    <div className="suggestions-list">
                                        {suggestions.length === 0 ? (
                                            <div className="suggestions-empty">
                                                <div style={{ fontSize: 32, marginBottom: 8 }}>🔍</div>
                                                <div>No inferred relations found</div>
                                            </div>
                                        ) : (
                                            suggestions.map((s, index) => {
                                                const isAccepted = acceptedSuggestions.includes(index);
                                                const isRejected = rejectedSuggestions.includes(index);

                                                const rel = (s.inferredRelation || '').toLowerCase();
                                                let relColor = '#22d3ee', relBg = 'rgba(34,211,238,0.1)', relBorder = 'rgba(34,211,238,0.25)';
                                                if (rel.includes('brother') || rel.includes('sister'))   { relColor = '#60a5fa'; relBg = 'rgba(96,165,250,0.1)';   relBorder = 'rgba(96,165,250,0.3)';   }
                                                else if (rel.includes('father') || rel.includes('mother'))  { relColor = '#a78bfa'; relBg = 'rgba(167,139,250,0.1)';  relBorder = 'rgba(167,139,250,0.3)';  }
                                                else if (rel.includes('son') || rel.includes('daughter'))   { relColor = '#34d399'; relBg = 'rgba(52,211,153,0.1)';   relBorder = 'rgba(52,211,153,0.3)';   }
                                                else if (rel.includes('grand'))                              { relColor = '#fbbf24'; relBg = 'rgba(251,191,36,0.1)';   relBorder = 'rgba(251,191,36,0.3)';   }
                                                else if (rel.includes('husband') || rel.includes('wife'))   { relColor = '#f472b6'; relBg = 'rgba(244,114,182,0.1)';  relBorder = 'rgba(244,114,182,0.3)';  }
                                                else if (rel.includes('friend'))                             { relColor = '#f59e0b'; relBg = 'rgba(245,158,11,0.1)';   relBorder = 'rgba(245,158,11,0.3)';   }

                                                const msg = s.message || '';
                                                let confidence = 'Medium', confColor = '#f59e0b', confBg = 'rgba(245,158,11,0.1)';
                                                if (msg.includes('high') || msg.includes('strong')) { confidence = 'High'; confColor = '#10b981'; confBg = 'rgba(16,185,129,0.1)'; }
                                                else if (msg.includes('low') || msg.includes('weak')) { confidence = 'Low'; confColor = '#ef4444'; confBg = 'rgba(239,68,68,0.1)'; }

                                                return (
                                                    <div
                                                        key={index}
                                                        className={`suggestion-row ${isAccepted ? 'accepted' : ''} ${isRejected ? 'rejected' : ''}`}
                                                    >
                                                        {/* Person A */}
                                                        <div className="suggestion-person">
                                                            <div className="suggestion-avatar" style={{ background: 'rgba(56,189,248,0.15)', color: '#38bdf8' }}>
                                                                {(s.personAName || '?').charAt(0).toUpperCase()}
                                                            </div>
                                                            <div className="suggestion-person-name" style={{ color: '#38bdf8' }}>
                                                                {s.personAName}
                                                            </div>
                                                        </div>

                                                        {/* Arrow + Relation chip */}
                                                        <div className="suggestion-relation-col">
                                                            <div className="suggestion-arrow">—</div>
                                                            <span style={{
                                                                color: relColor, background: relBg,
                                                                border: `1px solid ${relBorder}`,
                                                                borderRadius: 20, padding: '4px 12px',
                                                                fontSize: 12, fontWeight: 700,
                                                                whiteSpace: 'nowrap',
                                                            }}>
                                                                {(s.inferredRelation || 'Unknown').charAt(0).toUpperCase() + (s.inferredRelation || '').slice(1).toLowerCase()}
                                                            </span>
                                                            <div className="suggestion-arrow">—</div>
                                                        </div>

                                                        {/* Person B */}
                                                        <div className="suggestion-person">
                                                            <div className="suggestion-avatar" style={{ background: 'rgba(52,211,153,0.15)', color: '#34d399' }}>
                                                                {(s.personBName || '?').charAt(0).toUpperCase()}
                                                            </div>
                                                            <div className="suggestion-person-name" style={{ color: '#34d399' }}>
                                                                {s.personBName}
                                                            </div>
                                                        </div>

                                                        {/* Confidence */}
                                                        <div style={{ textAlign: 'center', minWidth: 64 }}>
                                                            <span style={{
                                                                color: confColor, background: confBg,
                                                                border: `1px solid ${confColor}40`,
                                                                borderRadius: 20, padding: '3px 10px',
                                                                fontSize: 11, fontWeight: 600,
                                                            }}>
                                                                {confidence}
                                                            </span>
                                                        </div>

                                                        {/* Actions */}
                                                        <div style={{ display: 'flex', gap: 6, flexShrink: 0 }}>
                                                            <Tooltip title={isAccepted ? 'Accepted' : 'Accept'}>
                                                                <button
                                                                    className={`sug-btn accept-btn ${isAccepted ? 'active' : ''}`}
                                                                    onClick={() => {
                                                                        if (isAccepted) {
                                                                            setAcceptedSuggestions(acceptedSuggestions.filter(i => i !== index));
                                                                        } else {
                                                                            setAcceptedSuggestions([...acceptedSuggestions, index]);
                                                                            setRejectedSuggestions(rejectedSuggestions.filter(i => i !== index));
                                                                        }
                                                                    }}
                                                                >
                                                                    <CheckCircleOutlined />
                                                                </button>
                                                            </Tooltip>
                                                            <Tooltip title={isRejected ? 'Rejected' : 'Reject'}>
                                                                <button
                                                                    className={`sug-btn reject-btn ${isRejected ? 'active' : ''}`}
                                                                    onClick={() => {
                                                                        if (isRejected) {
                                                                            setRejectedSuggestions(rejectedSuggestions.filter(i => i !== index));
                                                                        } else {
                                                                            setRejectedSuggestions([...rejectedSuggestions, index]);
                                                                            setAcceptedSuggestions(acceptedSuggestions.filter(i => i !== index));
                                                                        }
                                                                    }}
                                                                >
                                                                    <CloseCircleOutlined />
                                                                </button>
                                                            </Tooltip>
                                                        </div>
                                                    </div>
                                                );
                                            })
                                        )}
                                    </div>
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