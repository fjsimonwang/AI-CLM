package com.acme.clm.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) { this.jwt = jwt; }

    /** Re-authenticate on the async dispatch too, so SSE streaming endpoints stay authorized. */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() { return false; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims c = jwt.parse(header.substring(7));
                String roles = String.valueOf(c.get("roles", String.class));
                List<SimpleGrantedAuthority> auths = new java.util.ArrayList<>(Arrays.stream(roles.split(","))
                        .filter(s -> !s.isBlank())
                        .map(s -> new SimpleGrantedAuthority("ROLE_" + s.trim()))
                        .toList());
                Permissions.forRoles(roles).forEach(p -> auths.add(new SimpleGrantedAuthority("PERM_" + p)));
                var auth = new UsernamePasswordAuthenticationToken(c.getSubject(), null, auths);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}
