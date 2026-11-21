package uz.edubot.common.web;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Local development convenience: serves the built Mini App ({@code web/dist}) from the backend, so
 * one process on one port (and one tunnel) is enough. Client-side routes fall back to
 * {@code index.html}. In production the reverse proxy serves the files instead.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${edubot.frontend.dist-dir:}'.isBlank()")
class SpaConfig implements WebMvcConfigurer {

    private final String distLocation;

    SpaConfig(@Value("${edubot.frontend.dist-dir}") String distDir) {
        this.distLocation = Path.of(distDir).toAbsolutePath().normalize().toUri().toString();
    }

    /** The resource handler never sees the empty path, so "/" is forwarded explicitly. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(distLocation)
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource file = location.createRelative(resourcePath);
                        if (file.exists() && file.isReadable()) {
                            return file;
                        }
                        // Unknown API paths stay 404; everything else is a client-side route.
                        return resourcePath.startsWith("api/") ? null : location.createRelative("index.html");
                    }
                });
    }
}
