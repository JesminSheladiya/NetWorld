import { useState } from "react";
import { Form, Input, Button, Card, message, Typography } from "antd";
import { UserOutlined, LockOutlined, MailOutlined, PhoneOutlined } from "@ant-design/icons";
import { register } from "../Services/authService";

const { Title } = Typography;

function Register({ onRegisterSuccess, onSwitchForm }) {
  const [loading, setLoading] = useState(false);

  const onFinish = async (values) => {
    setLoading(true);
    try {
      const firstName = values.name.trim().split(" ")[0];

      const data = await register(
        firstName,
        values.email,
        values.phone,
        values.password,
        values.name.trim()
      );
      message.success(`Welcome, ${data.username}! Registration successful.`);
      onRegisterSuccess();
    } catch (error) {
      message.error(
        error.response?.data?.error ||
        error.response?.data?.message ||
        "Registration failed!"
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      display: "flex", justifyContent: "center",
      alignItems: "center", height: "100vh", background: "#141414"
    }}>
      <Card style={{ width: 420, boxShadow: "0 4px 12px rgba(0,0,0,0.3)" }}>
        <Title level={2} style={{ textAlign: "center", marginBottom: 24 }}>
          Net World Register
        </Title>

        <Form name="register" onFinish={onFinish} autoComplete="off" layout="vertical">

          <Form.Item name="name"
            rules={[
              { required: true, message: "Please enter your full name!" },
              {
                validator: (_, value) => {
                  if (!value) return Promise.resolve();
                  const words = value.trim().split(/\s+/);
                  if (words.length < 2)
                    return Promise.reject("Please enter at least 2 words (First Last)!");
                  return Promise.resolve();
                }
              }
            ]}>
            <Input prefix={<UserOutlined />} placeholder="Enter Name" size="large" />
          </Form.Item>

          <Form.Item name="email"
            rules={[
              { required: true, message: "Please enter email!" },
              { type: "email", message: "Please enter a valid email!" }
            ]}>
            <Input prefix={<MailOutlined />} placeholder="Email" size="large" />
          </Form.Item>

          <Form.Item name="phone"
            rules={[
              { required: true, message: "Please enter phone!" },
              { pattern: /^[0-9]{10}$/, message: "Phone must be 10 digits!" }
            ]}>
            <Input prefix={<PhoneOutlined />} placeholder="Phone (10 digits)" size="large" />
          </Form.Item>

          <Form.Item name="password"
            rules={[
              { required: true, message: "Please enter password!" },
              { min: 8, message: "Password must be at least 8 characters!" }
            ]}>
            <Input.Password prefix={<LockOutlined />} placeholder="Password" size="large" />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit"
              loading={loading} block size="large">
              Register
            </Button>
          </Form.Item>
        </Form>

        <div style={{ textAlign: "center", marginTop: 8 }}>
          <Typography.Text type="secondary">
            Already have an account?{" "}
            <a onClick={onSwitchForm} style={{ cursor: "pointer", color: "#177ddc" }}>
              Login here
            </a>
          </Typography.Text>
        </div>
      </Card>
    </div>
  );
}

export default Register;