-- S5/S6 risk mitigation: dedicated low-privilege role for n8n WF-001/WF-003 instead of reusing
-- a full ADMIN token (see s5-untrusted-content-and-residual-risks.md, riesgo #3).
INSERT INTO roles (code) VALUES ('AUTOMATION');
