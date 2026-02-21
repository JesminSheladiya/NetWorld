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
} from "antd";
import { EditOutlined, DeleteOutlined } from "@ant-design/icons";
import { useState, useEffect } from "react";
import { http } from "../Services/https"; // Use interceptor instance
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

    const [searchText, setSearchText] = useState("");
    const [messageApi, contextHolder] = message.useMessage();
    const [loading, setLoading] = useState(false);

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
            // example: search + pagination
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
            const response = await http.get(`${API_URL}/relations`); // Bearer token auto-added
            setRelations(response.data);
        } catch (error) {
            messageApi.error("Failed to load relations!");
            console.error(error);
        }
    };

    const fetchInferredRelations = async () => {
        try {
            const response = await http.get(`${API_URL}/inferred-relations`);
            setSuggestions(response.data);
            setShowSuggestions(true);
        } catch (error) {
            messageApi.error("Failed to load suggestions!");
        }
    };

    const handleAdd = () => {
        setEditingRecord(null);
        form.resetFields();
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
        setIsModalOpen(true);
    };


    const handleDelete = async (id) => {
        setLoading(true);
        try {
            await http.delete(`${API_URL}/${id}`); // Bearer token auto-added
            setDataSource(dataSource.filter((item) => item.key !== id));
            messageApi.success("Contact deleted successfully!");
        } catch (error) {
            messageApi.error("Failed to delete contact!");
            console.error(error);
        } finally {
            setLoading(false);
        }
    };

    const handleOk = () => {
        form
            .validateFields()
            .then(async (values) => {
                setIsModalOpen(false);
                setLoading(true);

                try {
                    let response;
                    if (editingRecord) {
                        response = await http.put(`${API_URL}/${editingRecord.id}`, values);
                        const updatedContact = response.data;
                        const relationObj = relations.find(r => r.id == updatedContact.relationId);

                        setDataSource((prev) =>
                            prev.map((item) =>
                                item.id === editingRecord.id
                                    ? {
                                        ...updatedContact,
                                        key: updatedContact.id,
                                        relation: relationObj || {
                                            id: updatedContact.relationId,
                                            relationName: `Relation ${updatedContact.relationId}`
                                        }
                                    }
                                    : item
                            )
                        );
                        messageApi.success("Contact updated successfully!");
                    }
                    else {
                        response = await http.post(API_URL, values);
                        const newContact = response.data;
                        const relationObj = relations.find(r => r.id == newContact.relationId);

                        const contactWithRelation = {
                            ...newContact,
                            key: newContact.id,
                            relation: relationObj || {
                                id: newContact.relationId,
                                relationName: `Relation ${newContact.relationId}`
                            }
                        };

                        setDataSource((prev) => [...prev, contactWithRelation]);
                        messageApi.success("Contact added successfully!");
                    }
                    form.resetFields();
                } catch (error) {
                    // error handling...
                } finally {
                    setLoading(false);
                }
            })
            .catch(() => {
                messageApi.error("Please fill all required fields!");
            });
    };


    // Filtered data for search
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
                if (!relation) return "—";
                const name = typeof relation === 'string'
                    ? relation
                    : (relation.relationName || relation || `Relation ${relation.id}`);
                return name.charAt(0).toUpperCase() + name.slice(1).toLowerCase();
            },
        },


        {
            title: "Actions",
            key: "actions",
            render: (_, record) => (
                <Space>
                    <Button
                        type="text"
                        size="middle"
                        icon={<EditOutlined />}
                        className="action-edit"
                        onClick={() => handleEdit(record)}
                    >
                        Edit
                    </Button>
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
                                >
                                    Relation Suggestions
                                </Button>

                            </Space>
                        </Space>

                        <Spin spinning={loading} tip="Processing..." size="large">
                            <Table
                                bordered
                                columns={columns}
                                dataSource={filteredData}
                                pagination={{ pageSize: 5 }}
                                className="contacts-table"
                                size="middle"
                            />
                            {showSuggestions && suggestions.length > 0 && (
                                <Card
                                    title="💡 Inferred Relations (Auto-Suggestions)"
                                    style={{ marginTop: 24, background: '#1e293b', border: '1px solid #334155' }}
                                    extra={
                                        <Button size="small" onClick={() => setShowSuggestions(false)}>
                                            Close
                                        </Button>
                                    }
                                >
                                    <Table
                                        size="small"
                                        pagination={{ pageSize: 5 }}
                                        dataSource={suggestions.map((s, i) => ({ ...s, key: i }))}
                                        columns={[
                                            { title: 'Person A', dataIndex: 'personAName', key: 'personAName' },
                                            {
                                                title: 'Inferred Relation', dataIndex: 'inferredRelation', key: 'inferredRelation',
                                                render: (rel) => <span style={{ color: '#60a5fa', fontWeight: 'bold' }}>{rel}</span>
                                            },
                                            { title: 'Person B', dataIndex: 'personBName', key: 'personBName' },
                                            {
                                                title: 'Message', dataIndex: 'message', key: 'message',
                                                render: (msg) => <span style={{ color: '#86efac' }}>{msg}</span>
                                            },
                                        ]}
                                    />
                                </Card>
                            )}

                        </Spin>
                    </Card>

                    <Modal
                        title={editingRecord ? "Edit Contact" : "Add Contact"}
                        open={isModalOpen}
                        onOk={handleOk}
                        onCancel={() => setIsModalOpen(false)}
                        okText="Save"
                        cancelText="Cancel"
                        className="contacts-modal"
                    >
                        <Form form={form} layout="vertical" className="contacts-form">
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