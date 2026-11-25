package com.suma.settings;

import com.suma.audit.AuditService;
import com.suma.common.ApiException;
import com.suma.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SettingsService {
    private final SystemSettingRepository repo;
    private final AuditService audit;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SettingsService(SystemSettingRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    public String get(String key, String def) {
        String v = cache.computeIfAbsent(key, k -> repo.findById(k).map(SystemSetting::getValue).orElse("\u0000"));
        return "\u0000".equals(v) || v == null ? def : v;
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Transactional(readOnly = true)
    public List<SystemSetting> all() {
        return repo.findAllByOrderByCategoryAscKeyAsc();
    }

    @Transactional
    public SystemSetting update(String key, String value) {
        SystemSetting s = repo.findById(key).orElseThrow(() -> ApiException.notFound("Setting " + key));
        if ("NUMBER".equals(s.getValueType())) {
            try {
                Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("Setting " + key + " must be a number");
            }
        }
        String old = s.getValue();
        s.setValue(value);
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(CurrentUser.id());
        cache.remove(key);
        audit.log("SETTING_UPDATED", "SystemSetting", key, old + " -> " + value);
        return s;
    }

    public void evictAll() {
        cache.clear();
    }
}
