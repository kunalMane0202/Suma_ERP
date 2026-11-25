package com.suma.client;

import com.suma.audit.AuditService;
import com.suma.common.ApiException;
import com.suma.common.PageResponse;
import com.suma.security.AccessScopeService;
import com.suma.security.CurrentUser;
import com.suma.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ClientService {
    private final ClientRepository clients;
    private final ClientContactRepository contacts;
    private final UserRepository users;
    private final AccessScopeService scope;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public ClientService(ClientRepository clients, ClientContactRepository contacts, UserRepository users,
                         AccessScopeService scope, AuditService audit, JdbcTemplate jdbc) {
        this.clients = clients;
        this.contacts = contacts;
        this.users = users;
        this.scope = scope;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientDtos.ClientResponse> list(String q, String status, int page, int size) {
        Set<UUID> ids = scope.visibleClientIds(CurrentUser.get());
        var p = clients.search(blank(q), blank(status), ids != null, ids == null || ids.isEmpty() ? Set.of(new UUID(0, 0)) : ids,
                PageRequest.of(page, Math.min(size, 200), Sort.by("name")));
        return PageResponse.of(p, this::toResponse);
    }

    @Transactional(readOnly = true)
    public ClientDtos.ClientResponse get(UUID id) {
        return toResponse(load(id));
    }

    @Transactional
    public ClientDtos.ClientResponse create(ClientDtos.ClientRequest r) {
        if (clients.existsByCodeIgnoreCase(r.code())) throw ApiException.conflict("Client code already exists");
        Client c = new Client();
        apply(c, r);
        clients.save(c);
        saveContacts(c.getId(), r.contacts());
        audit.log("CLIENT_CREATED", "Client", c.getId(), c.getCode() + " " + c.getName());
        return toResponse(c);
    }

    @Transactional
    public ClientDtos.ClientResponse update(UUID id, ClientDtos.ClientRequest r) {
        Client c = load(id);
        if (!c.getCode().equalsIgnoreCase(r.code()) && clients.existsByCodeIgnoreCase(r.code()))
            throw ApiException.conflict("Client code already exists");
        apply(c, r);
        if (r.contacts() != null) {
            contacts.deleteByClientId(id);
            contacts.flush();
            saveContacts(id, r.contacts());
        }
        audit.log("CLIENT_UPDATED", "Client", id, c.getCode());
        return toResponse(c);
    }

    @Transactional
    public void setStatus(UUID id, String status) {
        Client c = load(id);
        c.setStatus(status);
        audit.log("CLIENT_STATUS_CHANGED", "Client", id, status);
    }

    private Client load(UUID id) {
        Client c = clients.findById(id).orElseThrow(() -> ApiException.notFound("Client"));
        Set<UUID> ids = scope.visibleClientIds(CurrentUser.get());
        if (ids != null && !ids.contains(id)) throw ApiException.notFound("Client");
        return c;
    }

    private void apply(Client c, ClientDtos.ClientRequest r) {
        if (r.accountManagerId() != null && !users.existsById(r.accountManagerId()))
            throw ApiException.badRequest("Account manager not found");
        c.setCode(r.code().toUpperCase());
        c.setName(r.name().trim());
        c.setIndustry(r.industry());
        c.setEmail(r.email());
        c.setPhone(r.phone());
        c.setAddress(r.address());
        c.setWebsite(r.website());
        c.setAccountManagerId(r.accountManagerId());
        if (r.status() != null) c.setStatus(r.status());
        c.setConfiguration(r.configuration());
    }

    private void saveContacts(UUID clientId, List<ClientDtos.ContactDto> list) {
        if (list == null) return;
        for (var d : list) {
            ClientContact cc = new ClientContact();
            cc.setClientId(clientId);
            cc.setName(d.name());
            cc.setEmail(d.email());
            cc.setPhone(d.phone());
            cc.setRoleTitle(d.roleTitle());
            cc.setPrimary(d.primary());
            contacts.save(cc);
        }
    }

    private ClientDtos.ClientResponse toResponse(Client c) {
        String am = c.getAccountManagerId() == null ? null : users.findById(c.getAccountManagerId()).map(u -> u.getFullName()).orElse(null);
        List<ClientDtos.ContactDto> cs = contacts.findByClientIdOrderByPrimaryDescNameAsc(c.getId()).stream()
                .map(x -> new ClientDtos.ContactDto(x.getId(), x.getName(), x.getEmail(), x.getPhone(), x.getRoleTitle(), x.isPrimary())).toList();
        Long projects = jdbc.queryForObject("select count(*) from projects where client_id = ?", Long.class, c.getId());
        var counts = jdbc.queryForMap("select count(*) as total, count(*) filter (where status not in ('RESOLVED','CLOSED')) as open from tickets where client_id = ?", c.getId());
        return new ClientDtos.ClientResponse(c.getId(), c.getCode(), c.getName(), c.getIndustry(), c.getEmail(), c.getPhone(),
                c.getAddress(), c.getWebsite(), c.getAccountManagerId(), am, c.getStatus(), c.getConfiguration(), cs,
                projects == null ? 0 : projects, ((Number) counts.get("open")).longValue(), ((Number) counts.get("total")).longValue(), c.getCreatedAt());
    }

    static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

}
