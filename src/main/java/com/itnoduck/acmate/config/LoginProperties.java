package com.itnoduck.acmate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "acmate.login")
public class LoginProperties {
    private boolean emailEnabled;
    private String mailFrom = "";
    private boolean githubEnabled;
    private String githubClientId = "";
    private String githubClientSecret = "";
    private String githubCallbackUrl = "";
}
