import { useCallback, useEffect, useRef, useState } from "react";
import { AuthContext } from "./AuthContext.js";
import {
  getCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
} from "../services/session.js";

export default function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [status, setStatus] = useState("checking");
  const [sessionError, setSessionError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const operationInProgress = useRef(false);
  const expireSession = useCallback(() => {
  setUser(null);
  setSessionError("");
  setStatus("ready");
}, []);

  useEffect(() => {
    let active = true;

    async function restoreSession() {
      try {
        const account = await getCurrentUser();

        if (active) {
          setUser(account);
          setSessionError("");
          setStatus("ready");
        }
      } catch (error) {
        if (active) {
          setUser(null);
          setSessionError(error.message);
          setStatus("error");
        }
      }
    }

    restoreSession();

    return () => {
      active = false;
    };
  }, [attempt]);

  async function runOperation(operation) {
    if (operationInProgress.current) {
      throw new Error("Espera a que termine la operación actual.");
    }

    operationInProgress.current = true;
    setBusy(true);

    try {
      const account = await operation();
      setUser(account);
      setSessionError("");
      setStatus("ready");
    } finally {
      operationInProgress.current = false;
      setBusy(false);
    }
  }

  function login(credentials) {
    return runOperation(() => loginRequest(credentials));
  }

  function logout() {
    return runOperation(async () => {
      await logoutRequest();
      return null;
    });
  }

  function retrySession() {
    if (operationInProgress.current) return;

    setStatus("checking");
    setSessionError("");
    setAttempt((value) => value + 1);
  }

  return (
    <AuthContext.Provider
  value={{
    user,
    status,
    sessionError,
    busy,
    login,
    logout,
    retrySession,
    expireSession,
  }}
>
  {children}
</AuthContext.Provider>
  );
}