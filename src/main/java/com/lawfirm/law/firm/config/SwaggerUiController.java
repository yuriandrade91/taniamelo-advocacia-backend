package com.lawfirm.law.firm.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api")
public class SwaggerUiController {

    // Serve the static Swagger UI at /api/docs
    @GetMapping({"/docs", "/docs/"})
    public String docs() {
        return "forward:/api/docs/index.html";
    }

    // Legacy path: /api/swagger-ui -> forward to the explicit static page we created
    @GetMapping({"/swagger-ui", "/swagger-ui/"})
    public String swaggerUi() {
        return "forward:/api/swagger-ui/index.html";
    }

}
