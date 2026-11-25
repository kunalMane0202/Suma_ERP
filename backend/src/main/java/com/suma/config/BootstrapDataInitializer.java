package com.suma.config;

import com.suma.user.RoleRepository;
import com.suma.user.User;
import com.suma.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first administrator account if no administrator exists. Password comes from configuration. */
@Component
@Order(1)
public class BootstrapDataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapDataInitializer.class);
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final SumaProperties props;

    public BootstrapDataInitializer(UserRepository users, RoleRepository roles, PasswordEncoder encoder, SumaProperties props) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var admin = props.bootstrapAdmin();
        if (users.existsByUsernameIgnoreCase(admin.username())) return;
        User u = new User();
        u.setEmployeeCode("EMP-0001");
        u.setUsername(admin.username());
        u.setEmail(admin.email());
        u.setFullName("System Administrator");
        u.setJobTitle("Administrator");
        u.setRole(roles.findByCode("ADMIN").orElseThrow());
        u.setPasswordHash(encoder.encode(admin.password()));
        users.save(u);
        log.info("Bootstrap administrator '{}' created. Change the password after first login.", admin.username());
    }
}
