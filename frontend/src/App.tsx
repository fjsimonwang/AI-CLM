import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { useAuth } from "./api";
import { Layout } from "./components/Layout";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import Contracts from "./pages/Contracts";
import ContractDetail from "./pages/ContractDetail";
import Intake from "./pages/Intake";
import IntakeSessionView from "./pages/IntakeSessionView";
import Approvals from "./pages/Approvals";
import AutoReject from "./pages/AutoReject";
import Obligations from "./pages/Obligations";
import Clauses from "./pages/Clauses";
import Templates from "./pages/Templates";
import Access from "./pages/Access";
import AiLog from "./pages/AiLog";
import Admin from "./pages/Admin";

function Protected({ children, perm }: { children: JSX.Element; perm?: string }) {
  const token = useAuth((s) => s.token);
  const can = useAuth((s) => s.can);
  const loc = useLocation();
  if (!token) return <Navigate to="/login" state={{ from: loc }} replace />;
  const body = perm && !can(perm) ? <NoAccess /> : children;
  return <Layout>{body}</Layout>;
}

function NoAccess() {
  return (
    <div className="max-w-md mx-auto card p-8 text-center mt-10">
      <div className="text-lg font-medium mb-1">Not available for your role</div>
      <p className="text-sm text-ink-faint">
        You don't have permission to view this area. Contact an administrator if you need access.
      </p>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<Protected perm="VIEW_DASHBOARD"><Dashboard /></Protected>} />
      <Route path="/intake" element={<Protected perm="CREATE_INTAKE"><Intake /></Protected>} />
      <Route path="/intake-sessions/:id" element={<Protected><IntakeSessionView /></Protected>} />
      <Route path="/contracts" element={<Protected perm="VIEW_CONTRACTS"><Contracts /></Protected>} />
      <Route path="/contracts/:id" element={<Protected perm="VIEW_CONTRACTS"><ContractDetail /></Protected>} />
      <Route path="/inquiry" element={<Navigate to="/contracts" replace />} />
      <Route path="/approvals" element={<Protected perm="APPROVE"><Approvals /></Protected>} />
      <Route path="/auto-reject" element={<Protected perm="APPROVE"><AutoReject /></Protected>} />
      <Route path="/obligations" element={<Protected perm="VIEW_OBLIGATIONS"><Obligations /></Protected>} />
      <Route path="/clauses" element={<Protected perm="VIEW_CLAUSES"><Clauses /></Protected>} />
      <Route path="/templates" element={<Protected perm="VIEW_TEMPLATES"><Templates /></Protected>} />
      <Route path="/access" element={<Protected><Access /></Protected>} />
      <Route path="/ai-log" element={<Protected perm="VIEW_AI_LOG"><AiLog /></Protected>} />
      <Route path="/admin" element={<Protected perm="MANAGE_MASTERDATA"><Admin /></Protected>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
