import { useState, useEffect } from 'react';
import './App.css';
import ContactsTable from './components/ContactTable';
import Login from './components/Login';
import Register from './components/Register';
import UserProfile from "./components/UserProfile";
import { FiLogOut, FiUser } from 'react-icons/fi';
import { getToken, getUser, logout } from './Services/authService';
import { Dropdown, ConfigProvider, theme } from 'antd';

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [currentForm, setCurrentForm] = useState('login');
  const [profileOpen, setProfileOpen] = useState(false);
  const [currentUser, setCurrentUser] = useState(getUser());

  useEffect(() => {
    const token = getToken();
    if (token) {
      setIsAuthenticated(true);
      setCurrentUser(getUser());
    }
  }, []);

  const handleLoginSuccess = () => {
    setIsAuthenticated(true);
    setCurrentUser(getUser());
  };

  const handleLogout = () => {
    logout();
    setIsAuthenticated(false);
    setCurrentUser({});
  };

  const handleProfileUpdate = (updatedUser) => {
    setCurrentUser(updatedUser);
    setProfileOpen(false);
  };

  return (
    <div className="App">
      {isAuthenticated ? (
        <>
          <div style={{ position: 'absolute', top: 20, right: 20, zIndex: 1000 }}>
            <ConfigProvider theme={{ algorithm: theme.darkAlgorithm }}>
              <Dropdown
                menu={{
                  items: [
                    {
                      key: 'profile',
                      icon: <FiUser />,
                      label: 'Profile',
                      onClick: () => setProfileOpen(true),
                    },
                    {
                      key: 'logout',
                      icon: <FiLogOut />,
                      label: 'Logout',
                      danger: true,
                      onClick: handleLogout,
                    },
                  ],
                }}
                placement="bottomRight"
                trigger={['click']}
              >
                <div style={{
                  display: "flex", alignItems: "center", gap: 8, cursor: "pointer", color: "#38bdf8",
                  background: "rgba(56, 189, 248, 0.1)", padding: "8px 16px", borderRadius: "20px",
                  border: "1px solid rgba(56, 189, 248, 0.3)", transition: "all 0.3s ease"
                }}
                onMouseEnter={(e) => e.currentTarget.style.background = "rgba(56, 189, 248, 0.2)"}
                onMouseLeave={(e) => e.currentTarget.style.background = "rgba(56, 189, 248, 0.1)"}
                >
                  <FiUser style={{ fontSize: 18 }} />
                  <span style={{ fontSize: 15, fontWeight: 500 }}>
                    {currentUser.fullName || currentUser.username}
                  </span>
                </div>
              </Dropdown>
            </ConfigProvider>
          </div>

          <ContactsTable />

          <UserProfile
            open={profileOpen}
            onClose={() => setProfileOpen(false)}
            onProfileUpdate={handleProfileUpdate}
          />
        </>
      ) : currentForm === 'login' ? (
        <Login
          onLoginSuccess={handleLoginSuccess}
          onSwitchForm={() => setCurrentForm('register')}
        />
      ) : (
        <Register
          onRegisterSuccess={handleLoginSuccess}
          onSwitchForm={() => setCurrentForm('login')}
        />
      )}
    </div>
  );
}

export default App;