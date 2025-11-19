package com.phegon.phegonbank.config;

import com.phegon.phegonbank.role.entity.Role;
import com.phegon.phegonbank.role.repo.RoleRepo;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Configuration
public class AppConfig {

    @Bean
    public SpringTemplateEngine templateEngine() {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();

        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setCharacterEncoding("UTF-8");

        templateEngine.setTemplateResolver(templateResolver);
        return templateEngine;
    }

    @Bean
    public ModelMapper modelMapperConfig() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setFieldMatchingEnabled(true)
                .setFieldAccessLevel(org.modelmapper.config.Configuration.AccessLevel.PRIVATE)
                .setMatchingStrategy(MatchingStrategies.STANDARD);

        return modelMapper;
    }

    @Bean
    public CommandLineRunner initRoles(RoleRepo roleRepo) {
        return args -> {
            addRoleIfNotExists(roleRepo, "ADMIN");
            addRoleIfNotExists(roleRepo, "AUDITOR");
            addRoleIfNotExists(roleRepo, "CUSTOMER");
        };
    }

    private void addRoleIfNotExists(RoleRepo repo, String roleName) {
        if (!repo.existsByName(roleName)) {
            repo.save(Role.builder().name(roleName).build());
        }
    }
}
