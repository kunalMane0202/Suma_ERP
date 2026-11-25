export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }

export interface ApiError { status: number; code: string; message: string; fieldErrors?: Record<string, string>; correlationId?: string }

export interface ProjectRef { id: string; code: string; name: string; designation: string }

export interface Me {
  id: string; employeeCode: string; username: string; fullName: string; email: string; role: Role;
  permissions: string[]; themePreference: 'LIGHT' | 'DARK'; timezone?: string; sessionId: string;
  projects: ProjectRef[]; projectLead: boolean; teamLeader: boolean;
}

export type Role = 'ADMIN' | 'MANAGER' | 'TEAM_LEADER' | 'L1' | 'L2' | 'L3';

export interface TokenResponse { accessToken: string; expiresInSeconds: number; user: Me }

export interface UserSummary { id: string; employeeCode: string; fullName: string; role: Role; email: string }

export interface Client {
  id: string; code: string; name: string; industry?: string; email?: string; phone?: string; address?: string; website?: string;
  accountManagerId?: string; accountManagerName?: string; status: 'ACTIVE' | 'INACTIVE'; configuration?: string;
  contacts: Contact[]; projectCount: number; openTickets: number; totalTickets: number; createdAt: string;
}
export interface Contact { id?: string; name: string; email?: string; phone?: string; roleTitle?: string; primary: boolean }

export interface ProjectMember { userId: string; employeeCode: string; fullName: string; role: Role; designation: string; allocationPercent: number; primary: boolean; assignedAt: string }

export interface Project {
  id: string; clientId: string; clientName?: string; code: string; name: string; description?: string;
  managerId?: string; managerName?: string; teamLeaderId?: string; teamLeaderName?: string;
  projectLeadId?: string; projectLeadName?: string; projectLeadEmail?: string; projectLeadEmailVerified: boolean;
  calendarId?: string; supportHours?: string; timezone: string; status: 'ACTIVE' | 'INACTIVE'; startDate?: string; endDate?: string;
  memberCount: number; openTickets: number; totalTickets: number; createdAt: string; members?: ProjectMember[];
}

export interface Category { id: string; projectId?: string; parentId?: string; name: string; keywords?: string; defaultTeamId?: string }

export interface SlaInfo {
  responseDueAt?: string; resolutionDueAt?: string; firstResponseAt?: string; responseBreached: boolean; resolutionBreached: boolean;
  paused: boolean; state: 'NO_SLA' | 'ON_TRACK' | 'AT_RISK' | 'BREACHED' | 'MET' | 'PAUSED'; resolutionRemainingSeconds?: number; policyName?: string;
}

export interface TicketSummary {
  id: string; ticketNumber: string; title: string; status: TicketStatus; priority: Priority; projectId: string; projectName?: string;
  clientId: string; clientName?: string; assigneeId?: string; assigneeName?: string; teamId?: string; teamName?: string; categoryName?: string;
  source: string; escalationLevel: number; createdAt: string; updatedAt: string; ageHours: number; sla: SlaInfo;
}
export type TicketStatus = 'NEW' | 'ASSIGNED' | 'IN_PROGRESS' | 'PENDING' | 'RESOLVED' | 'CLOSED' | 'REOPENED';
export type Priority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export interface TicketDetail {
  summary: TicketSummary; description?: string; categoryId?: string; subcategoryId?: string; reporterName?: string; reporterEmail?: string;
  externalSystem?: string; externalId?: string; reopenedCount: number; resolvedAt?: string; closedAt?: string;
  customFields: Record<string, unknown>;
  comments: { id: string; authorId?: string; authorName?: string; body: string; internal: boolean; createdAt: string }[];
  attachments: { id: string; fileName: string; contentType: string; sizeBytes: number; uploadedByName?: string; createdAt: string }[];
  history: { id: string; action: string; field?: string; oldValue?: string; newValue?: string; note?: string; actorName?: string; createdAt: string }[];
  allowedTransitions: TicketStatus[];
}

export interface Team {
  id: string; name: string; description?: string; projectId?: string; projectName?: string; teamLeaderId?: string; teamLeaderName?: string;
  active: boolean; members: { id: string; employeeCode: string; fullName: string; role: Role; openTickets: number }[];
  openTickets: number; resolvedLast30Days: number;
}

export interface UserRow {
  id: string; employeeCode: string; username: string; email: string; fullName: string; phone?: string; jobTitle?: string; role: Role;
  shiftId?: string; shiftName?: string; weeklyOffs: number[]; timezone?: string; maxOpenTickets?: number; status: string;
  lastLoginAt?: string; lastLoginIp?: string; createdAt: string;
  projects: { projectId: string; projectCode: string; projectName: string; designation: string; allocationPercent: number; primary: boolean }[];
  teams: string[]; openTickets?: number;
}

export interface Shift { id: string; name: string; startTime: string; endTime: string; timezone: string; active: boolean }
