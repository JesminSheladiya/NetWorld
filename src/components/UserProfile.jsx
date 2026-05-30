import { useState } from "react";
import {
    Modal, Form, Input, Button, message, Avatar,
    Typography, Divider, Tag, Upload
} from "antd";
import {
    UserOutlined, MailOutlined, PhoneOutlined,
    LockOutlined, EditOutlined, CameraOutlined
} from "@ant-design/icons";
import ImgCrop from "antd-img-crop";
import { updateProfile, getUser } from "../Services/authService";

const { Title, Text } = Typography;

function UserProfile({ open, onClose, onProfileUpdate }) {
    const user = getUser();
    const [editing, setEditing] = useState(false);
    const [loading, setLoading] = useState(false);
    const [form] = Form.useForm();
    const [previewImage, setPreviewImage] = useState(user.profilePicture || null);
    const [newImageBase64, setNewImageBase64] = useState(null);

    const handleEdit = () => {
        form.setFieldsValue({ fullName: user.fullName, phone: user.phone });
        setPreviewImage(user.profilePicture || null);
        setNewImageBase64(null);
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
                ...(newImageBase64 !== null && { profilePicture: newImageBase64 }),
            });
            message.success("Profile updated successfully!");
            setEditing(false);
            onProfileUpdate(updated);
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
            width={500}
            centered
            closable={true}
            title={!editing ? null : <span style={{ color: '#0ea5e9', fontSize: 18, fontWeight: 700 }}>Edit Profile</span>}
            styles={{
                content: {
                    background: 'linear-gradient(135deg, rgba(15, 23, 42, 0.97) 0%, rgb(2 0 15) 100%)',
                    border: '1px solid rgba(59, 130, 246, 0.25)',
                    borderRadius: 15,
                    overflow: 'hidden',
                    padding: 0
                },
                header: {
                    background: 'linear-gradient(135deg, rgba(15, 23, 42, 0.97) 0%)',
                    borderBottom: '1px solid rgba(59, 130, 246, 0.15)',
                    padding: '20px 24px',
                    margin: 0
                },
                body: {
                    background: 'linear-gradient(135deg, rgba(15, 23, 42, 0.97) 0%, rgb(2 0 15) 100%)',
                    color: '#f8fafc',
                    padding: editing ? '24px 32px' : '28px 24px'
                },
                mask: { backgroundColor: 'rgba(0, 0, 0, 0.5)', backdropFilter: 'blur(8px)' },
            }}
            modalRender={(modal) => (
                <div style={{ boxShadow: '0 25px 80px rgba(0,0,0,0.45), 0 0 1px rgba(14,165,233,0.2)' }}>
                    {modal}
                </div>
            )}
        >
            {/* ── View Mode ── */}
            {!editing && (
                <div style={{ textAlign: "center", paddingTop: "12px" }}>
                    <Avatar
                        size={90}
                        src={user.profilePicture || null}
                        icon={!user.profilePicture && <UserOutlined />}
                        style={{ background: "#0ea5e9", marginBottom: 20, color: '#ffffff' }}
                    />
                    <Title level={3} style={{ marginBottom: 8, color: '#f8fafc', fontWeight: 700 }}>
                        {user.fullName || user.username}
                    </Title>
                    <Tag color="cyan" style={{ marginBottom: 24 }}>{user.role || "USER"}</Tag>

                    <Divider style={{ borderColor: 'rgba(14,165,233,0.1)', margin: '20px 0' }} />

                    <div style={{ textAlign: "left" }}>
                        <div style={{ marginBottom: 14, display: 'flex', alignItems: 'center', gap: 10 }}>
                            <MailOutlined style={{ color: '#0ea5e9', fontSize: 16 }} />
                            <Text style={{ color: '#cbd5e1' }}>
                                <span style={{ color: '#94a3b8' }}>Email: </span>{user.email}
                            </Text>
                        </div>
                        <div style={{ marginBottom: 14, display: 'flex', alignItems: 'center', gap: 10 }}>
                            <PhoneOutlined style={{ color: '#0ea5e9', fontSize: 16 }} />
                            <Text style={{ color: '#cbd5e1' }}>
                                <span style={{ color: '#94a3b8' }}>Phone: </span>{user.phone || "—"}
                            </Text>
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                            <UserOutlined style={{ color: '#0ea5e9', fontSize: 16 }} />
                            <Text style={{ color: '#cbd5e1' }}>
                                <span style={{ color: '#94a3b8' }}>Username: </span>{user.username}
                            </Text>
                        </div>
                    </div>

                    <Divider style={{ borderColor: 'rgba(14,165,233,0.1)', margin: '20px 0' }} />
                    <Button type="primary" icon={<EditOutlined />} onClick={handleEdit}>
                        Edit Profile
                    </Button>
                </div>
            )}

            {/* ── Edit Mode ── */}
            {editing && (
                <Form form={form} layout="vertical" onFinish={handleSave}>

                    <Form.Item style={{ marginBottom: 24 }}>
                        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
                            <ImgCrop rotationSlider aspect={1} showGrid>
                                <Upload
                                    showUploadList={false}
                                    customRequest={() => { }}
                                    beforeUpload={(file) => {
                                        if (!file.type.startsWith("image/")) {
                                            message.error("Only images allowed!");
                                            return Upload.LIST_IGNORE;
                                        }
                                        if (file.size > 5 * 1024 * 1024) {
                                            message.error("Max 5MB!");
                                            return Upload.LIST_IGNORE;
                                        }
                                        const reader = new FileReader();
                                        reader.onload = (e) => {
                                            setPreviewImage(e.target.result);
                                            setNewImageBase64(e.target.result);
                                        };
                                        reader.readAsDataURL(file);
                                        return false;
                                    }}
                                >
                                    <div style={{ cursor: 'pointer', position: 'relative', display: 'inline-block' }}>
                                        <Avatar
                                            size={90}
                                            src={previewImage || null}
                                            icon={!previewImage && <UserOutlined />}
                                            style={{ background: '#0ea5e9', color: '#fff' }}
                                        />
                                        <div style={{
                                            position: 'absolute', bottom: 0, right: 0,
                                            background: '#0ea5e9', borderRadius: '50%',
                                            width: 28, height: 28,
                                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                                            border: '2px solid #0f172a'
                                        }}>
                                            <CameraOutlined style={{ color: '#fff', fontSize: 13 }} />
                                        </div>
                                    </div>
                                </Upload>
                            </ImgCrop>
                            {previewImage && (
                                <Button type="link" danger size="small"
                                    onClick={() => {
                                        setPreviewImage(null);
                                        setNewImageBase64("");
                                    }}>
                                    Remove Photo
                                </Button>
                            )}
                        </div>
                    </Form.Item>

                    <Form.Item
                        name="fullName"
                        label={<span style={{ color: '#cbd5e1', fontWeight: 500 }}>Full Name</span>}
                        style={{ marginBottom: 18 }}
                    >
                        <Input
                            prefix={<UserOutlined style={{ color: '#0ea5e9', marginRight: 8 }} />}
                            placeholder="Full Name"
                            style={{ backgroundColor: '#111827', borderColor: 'rgba(14,165,233,0.2)', color: '#f8fafc', height: 45, borderRadius: 12, fontSize: 14 }}
                            className="profile-input"
                        />
                    </Form.Item>

                    <Form.Item
                        name="phone"
                        label={<span style={{ color: '#cbd5e1', fontWeight: 500 }}>Phone</span>}
                        rules={[{ pattern: /^[0-9]{10}$/, message: "10 digits required" }]}
                        style={{ marginBottom: 18 }}
                    >
                        <Input
                            prefix={<PhoneOutlined style={{ color: '#0ea5e9', marginRight: 8 }} />}
                            placeholder="10-digit phone"
                            style={{ backgroundColor: '#111827', borderColor: 'rgba(14,165,233,0.2)', color: '#f8fafc', height: 45, borderRadius: 12, fontSize: 14 }}
                            className="profile-input"
                        />
                    </Form.Item>

                    <Divider style={{ borderColor: 'rgba(14,165,233,0.15)', marginTop: 24, marginBottom: 22 }} />

                    <Title level={5} style={{ color: '#f8fafc', marginBottom: 16, fontWeight: 600, fontSize: 15 }}>
                        Change Password (optional)
                    </Title>

                    <Form.Item
                        name="currentPassword"
                        label={<span style={{ color: '#cbd5e1', fontWeight: 500 }}>Current Password</span>}
                        style={{ marginBottom: 18 }}
                    >
                        <Input.Password
                            prefix={<LockOutlined style={{ color: '#0ea5e9', marginRight: 8 }} />}
                            placeholder="Current password"
                            style={{ backgroundColor: '#111827', borderColor: 'rgba(14,165,233,0.2)', color: '#f8fafc', height: 45, borderRadius: 12, fontSize: 14 }}
                            className="profile-input"
                        />
                    </Form.Item>

                    <Form.Item
                        name="newPassword"
                        label={<span style={{ color: '#cbd5e1', fontWeight: 500 }}>New Password</span>}
                        rules={[{ min: 8, message: "Min 8 characters" }]}
                        style={{ marginBottom: 24 }}
                    >
                        <Input.Password
                            prefix={<LockOutlined style={{ color: '#0ea5e9', marginRight: 8 }} />}
                            placeholder="New password"
                            style={{ backgroundColor: '#111827', borderColor: 'rgba(14,165,233,0.2)', color: '#f8fafc', height: 45, borderRadius: 12, fontSize: 14 }}
                            className="profile-input"
                        />
                    </Form.Item>

                    <div style={{ display: "flex", gap: 12, justifyContent: "flex-end", marginTop: 28 }}>
                        <Button onClick={() => setEditing(false)} size="large">Cancel</Button>
                        <Button type="primary" htmlType="submit" loading={loading} size="large">
                            Save Changes
                        </Button>
                    </div>
                </Form>
            )}
        </Modal>
    );
}

export default UserProfile;