import axios from "axios";

// Keep contacts URL in .env as before
// REACT_APP_API_URL=http://localhost:8080/api/contacts

// Derive API base safely
const CONTACTS_URL = process.env.REACT_APP_API_URL || "http://localhost:8080/api/contacts";
const API_BASE = CONTACTS_URL.replace(/\/api\/contacts.*/,'/api'); // -> http://localhost:8080/api

export const register = async (username, email, password) => {
  const { data } = await axios.post(`${API_BASE}/auth/register`,
    { username, email, password },
    { headers: { "Content-Type": "application/json" } }
  );
  if (data.token) {
    localStorage.setItem("token", data.token);
    localStorage.setItem("user", JSON.stringify({ username: data.username, email: data.email }));
  }
  return data;
};

export const login = async (username, password) => {
  const { data } = await axios.post(`${API_BASE}/auth/login`,
    { username, password },
    { headers: { "Content-Type": "application/json" } }
  );
  if (data.token) {
    localStorage.setItem("token", data.token);
    localStorage.setItem("user", JSON.stringify({ username: data.username, email: data.email }));
  }
  return data;
};

export const getToken = () => localStorage.getItem("token");
export const logout = () => { localStorage.removeItem("token"); localStorage.removeItem("user"); };
