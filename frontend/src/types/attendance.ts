export type AttendanceState = 'WORKING' | 'ON_BREAK' | 'IDLE' | 'AWAY' | 'OFFLINE';

export interface BreakDto {
  id: string; userId: string; category: string; startedAt: string; endedAt?: string; durationSeconds: number; status: string;
  exceeded: boolean; limitMinutes?: number; startSource: string; endSource?: string; notes?: string; workDate: string;
}
export interface IdleDto {
  id: string; idleStartedAt: string; detectedAt: string; endedAt?: string; durationSeconds?: number; status: string;
  response?: 'WORKING' | 'ON_BREAK' | 'AWAY'; respondedAt?: string; source: string;
}
export interface SessionDto {
  id: string; authSessionId: string; loginAt: string; logoutAt?: string; logoutSource?: string; status: string; currentState: AttendanceState;
  loginIp?: string; device?: string; browser?: string; durationSeconds: number; lastHeartbeatAt?: string; workDate: string;
}
export interface TodayView {
  workDate: string; timezone: string; currentSession?: SessionDto; currentState: AttendanceState; currentBreak?: BreakDto;
  firstLoginAt?: string; sessionSeconds: number; breakCount: number; breakSeconds: number; averageBreakSeconds: number;
  idleCount: number; idleSeconds: number; breaks: BreakDto[]; idles: IdleDto[]; sessions: SessionDto[]; exceptions: string[];
  idleThresholdMinutes: number; maxBreakMinutes?: number; maxDailyBreakMinutes?: number; maxBreaksPerDay?: number;
  breakCategories: { code: string; label: string; active: boolean }[]; openIdle?: IdleDto;
}
export interface IdleStatus { thresholdMinutes: number; currentState: AttendanceState; openIdle?: IdleDto; lastActivityAt?: string; systemWideMonitoring: boolean; scopeNotice: string }

export interface LiveRow {
  userId: string; employeeCode: string; fullName: string; projectName?: string; teamName?: string; status: string; loginAt?: string;
  lastActivityAt?: string; breakStartedAt?: string; breakCategory?: string; todayBreakSeconds: number; todayBreakCount: number;
  breakExceeded: boolean; idleSince?: string;
}
export interface AttendanceDashboard {
  date: string; timezone: string; employeesInScope: number; loggedInToday: number; currentlyActive: number; onBreak: number; idle: number; away: number;
  loggedOut: number; notLoggedIn: number; missingLogout: number; totalBreaksToday: number; averageBreakSeconds: number;
  exceedingBreakLimits: number; attendanceExceptions: number; compliancePercent: number; live: LiveRow[];
  loginsByHour: { hour: number; logins: number; breaks: number }[];
}

export interface SessionLine { id: string; loginAt: string; logoutAt?: string; durationSeconds: number; status: string; logoutSource?: string; loginIp?: string; ongoing: boolean }
export interface BreakLine { id: string; category: string; startedAt: string; endedAt?: string; durationSeconds: number; exceeded: boolean; status: string; ongoing: boolean }
export interface IdleLine { id: string; startedAt: string; endedAt?: string; durationSeconds: number; status: string; response?: string }
export interface DayResult {
  date: string; present: boolean; firstLoginAt?: string; lastLogoutAt?: string; sessions: SessionLine[]; sessionSeconds: number;
  breaks: BreakLine[]; breakCount: number; breakSeconds: number; averageBreakSeconds: number; exceededBreakCount: number;
  idles: IdleLine[]; idleCount: number; idleSeconds: number; unconfirmedIdleCount: number; missingLogout: boolean; weeklyOff: boolean; exceptions: string[];
}
export interface ReportEmployee { userId: string; employeeCode: string; fullName: string; email: string; projectId?: string; projectName?: string; teamId?: string; teamName?: string }
export interface Totals { employees: number; present: number; sessionSeconds: number; breakCount: number; breakSeconds: number; averageBreakSeconds: number; idleCount: number; idleSeconds: number; exceptions: number; missingLogouts: number }
export interface DailyReport { date: string; timezone: string; generatedAt: string; rows: { employee: ReportEmployee; day: DayResult }[]; totals: Totals }
export interface MonthlyEmployee {
  employee: ReportEmployee; days: DayResult[]; daysPresent: number; sessionSeconds: number; breakCount: number; breakSeconds: number;
  averageBreakSeconds: number; idleCount: number; idleSeconds: number; exceptionCount: number; missingLogoutCount: number; missingLoginDays: number; exceededBreakCount: number;
}
export interface MonthlyReport { year: number; month: number; timezone: string; generatedAt: string; employees: MonthlyEmployee[]; totals: Totals }

export interface ReportLog {
  id: string; projectId: string; reportType: string; periodYear: number; periodMonth: number; recipientEmail?: string; status: string;
  attempts: number; employeeCount: number; lastAttemptAt?: string; nextRetryAt?: string; lastError?: string; generatedAt?: string;
  sentAt?: string; fileName?: string; triggeredBy: string; createdAt: string;
}
export interface AttendanceSettings {
  id?: string; projectId?: string; defaultWorkHours?: number; idleThresholdMinutes: number; heartbeatTimeoutMinutes: number;
  missingLogoutAfterHours: number; maxBreakMinutes?: number; maxDailyBreakMinutes?: number; maxBreaksPerDay?: number;
  breakReminderIntervalMinutes?: number; reportingTimezone: string; notifyTeamLeaderOnIdle: boolean; monthlyReportEnabled: boolean;
  monthlyReportHour: number; emailMaxAttempts: number; emailRetryBackoffMinutes: number; retentionDays: number; updatedAt?: string;
}
