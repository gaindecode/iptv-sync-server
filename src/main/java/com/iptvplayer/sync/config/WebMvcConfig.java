package com.iptvplayer.sync.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Redirige /portal (sans slash final) vers /portal/index.html.
 *
 * Sans cette config, Spring Boot sert les fichiers statiques de
 * static/portal/ uniquement sur des chemins exacts comme /portal/
 * ou /portal/index.html — /portal seul (l'URL affichée sur la TV,
 * voir PairingScreen.tsx portalDisplayUrl) tombe sur le DispatcherServlet
 * REST et renvoie l'erreur JSON du GlobalExceptionHandler (404 non géré
 * explicitement → 500 générique).
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/portal", "/portal/index.html");
        registry.addRedirectViewController("/portal/", "/portal/index.html");
    }
}
