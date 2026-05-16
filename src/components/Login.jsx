import { useState } from "react";
import { Form, Input, Button, Card, message, Typography } from "antd";
import { UserOutlined, LockOutlined } from "@ant-design/icons";
import { login } from "../Services/authService";

const { Title } = Typography;

function Login({ onLoginSuccess, onSwitchForm }) {
  const [loading, setLoading] = useState(false);

  const onFinish = async (values) => {
    setLoading(true);
    try {
      const data = await login(values.identifier, values.password);
      message.success(`Welcome, ${data.username}!`);
      onLoginSuccess();
    } catch (error) {
      message.error(
        error.response?.data?.error ||
        error.response?.data?.message ||
        "Login failed!"
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
      <Card style={{ width: 400, boxShadow: "0 4px 12px rgba(0,0,0,0.3)" }}>
        <Title level={2} style={{ textAlign: "center", marginBottom: 30 }}>
          Net World Login
        </Title>

        <Form name="login" onFinish={onFinish} autoComplete="off" layout="vertical">

          <Form.Item name="identifier"
            rules={[{ required: true, message: "Please enter email or phone!" }]}>
            <Input
              prefix={<UserOutlined />}
              placeholder="Email / Phone"
              size="large"
            />
          </Form.Item>

          <Form.Item name="password"
            rules={[{ required: true, message: "Please enter password!" }]}>
            <Input.Password prefix={<LockOutlined />}
              placeholder="Password" size="large" />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit"
              loading={loading} block size="large">
              Login
            </Button>
          </Form.Item>
        </Form>

        <div style={{ textAlign: "center", marginTop: 16 }}>
          <Typography.Text type="secondary">
            Don't have an account?{" "}
            <a onClick={onSwitchForm} style={{ cursor: "pointer", color: "#177ddc" }}>
              Register here
            </a>
          </Typography.Text>
        </div>
      </Card>
    </div>
  );
}

export default Login;