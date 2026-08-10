package com.systemdesign.echobackend;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal stub backend (PROBLEM.md FR6) proxied to by the rate limiter on allowed requests. */
@RestController
public class EchoController {

    @RequestMapping("/**")
    public ResponseEntity<Map<String, Object>> echo(HttpServletRequest request,
                                                      @RequestBody(required = false) String body) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("method", request.getMethod());
        response.put("path", request.getRequestURI());
        response.put("query", request.getQueryString());
        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames()).forEach(name -> headers.put(name, request.getHeader(name)));
        response.put("headers", headers);
        response.put("body", body);
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }
}
