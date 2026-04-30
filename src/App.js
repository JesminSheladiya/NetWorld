import { useState, useEffect } from 'react';
import './App.css';
import ContactsTable from './components/ContactTable';
import Login from './components/Login';
import Register from './components/Register';
import { FiLogOut } from 'react-icons/fi';

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [currentForm, setCurrentForm] = useState('login');

  useEffect(() => {
    // Check if user is already logged in
    const token = localStorage.getItem("token");
    if (token) {
      setIsAuthenticated(true);
    }
  }, []);

  const handleLoginSuccess = () => {
    setIsAuthenticated(true);
  };

  const handleLogout = () => {
    localStorage.clear();
    setIsAuthenticated(false);
  };

  return (
    <div className="App">
      {isAuthenticated ? (
        <>
          <div style={{ position: 'absolute', top: 20, right: 20, zIndex: 1000 }}>
            <FiLogOut
              onClick={handleLogout}
              style={{
                fontSize: '22px',
                color: '#fff',
                cursor: 'pointer'
              }}
              title="Logout"
            />
          </div>
          <ContactsTable />
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
