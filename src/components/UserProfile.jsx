import { useState } from "react";
import {
    Modal, Form, Input, Button, message, Avatar,
    Typography, Divider, Tag
} from "antd";
import {
    UserOutlined, MailOutlined, PhoneOutlined,
    LockOutlined, EditOutlined
} from "@ant-design/icons";
import { updateProfile, getUser } from "../Services/authService";

const { Title, Text } = Typography;

function UserProfile({ open, onClose, onProfileUpdate }) {
    const user = getUser();
    const [editing, setEditing] = useState(false);
    const [loading, setLoading] = useState(false);
    const [form] = Form.useForm();

    const handleEdit = () => {
        form.setFieldsValue({
            fullName: user.fullName,
            phone: user.phone,
        });
        setEditing(true);
    };

    const handleSave = async (values) => {
        setLoading(true);
        try {
            const updated = await updateProfile({
                fullName: values.fullName,
                phone: values.phone,
                currentPassword: values.currentPassword,
                newPassword: values.newPassword,
            });
            message.success("Profile updated successfully!");
            setEditing(false);
            onProfileUpdate(updated); // App.js ko updated user bhejna
        } catch (err) {
            message.error(
                err.response?.data?.message ||
                err.response?.data?.error ||
                "Update failed!"
            );
        } finally {
            setLoading(false);
        }
    };

    return (
        <Modal
            open={open}
            onCancel={() => { setEditing(false); onClose(); }}
            footer={null}
            width={440}
            centered
        >
            {/* ── View Mode ── */}
            {!editing && (
                <div style={{ textAlign: "center", padding: "12px 0" }}>
                    <Avatar size={80} icon={<UserOutlined />}
                        style={{ background: "#177ddc", marginBottom: 16 }} />
                    <Title level={4} style={{ marginBottom: 4 }}>
                        {user.fullName || user.username}
                    </Title>
                    <Tag color="blue">{user.role || "USER"}</Tag>

                    <Divider />

                    <div style={{ textAlign: "left", padding: "0 16px" }}>
                        <p><MailOutlined style={{ marginRight: 8 }} />
                            <Text strong>Email: </Text>{user.email}
                        </p>
                        <p><PhoneOutlined style={{ marginRight: 8 }} />
                            <Text strong>Phone: </Text>{user.phone || "—"}
                        </p>
                        <p><UserOutlined style={{ marginRight: 8 }} />
                            <Text strong>Username: </Text>{user.username}
                        </p>
                    </div>

                    <Divider />

                    <Button type="primary" icon={<EditOutlined />} onClick={handleEdit}>
                        Edit Profile
                    </Button>
                </div>
            )}

            {/* ── Edit Mode ── */}
            {editing && (
                <>
                    <Title level={4} style={{ marginBottom: 20 }}>Edit Profile</Title>
                    <Form form={form} layout="vertical" onFinish={handleSave}>

                        <Form.Item name="fullName" label="Full Name">
                            <Input prefix={<UserOutlined />} placeholder="Full Name" />
                        </Form.Item>

                        <Form.Item name="phone" label="Phone"
                            rules={[{ pattern: /^[0-9]{10}$/, message: "10 digits required" }]}>
                            <Input prefix={<PhoneOutlined />} placeholder="10-digit phone" />
                        </Form.Item>

                        <Divider orientation="left">Change Password (optional)</Divider>

                        <Form.Item name="currentPassword" label="Current Password">
                            <Input.Password prefix={<LockOutlined />} placeholder="Current password" />
                        </Form.Item>

                        <Form.Item name="newPassword" label="New Password"
                            rules={[{ min: 8, message: "Min 8 characters" }]}>
                            <Input.Password prefix={<LockOutlined />} placeholder="New password" />
                        </Form.Item>

                        <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
                            <Button onClick={() => setEditing(false)}>Cancel</Button>
                            <Button type="primary" htmlType="submit" loading={loading}>
                                Save Changes
                            </Button>
                        </div>
                    </Form>
                </>
            )}
        </Modal>
    );
}

export default UserProfile;