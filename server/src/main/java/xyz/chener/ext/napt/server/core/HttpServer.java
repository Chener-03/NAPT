package xyz.chener.ext.napt.server.core;

import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.security.RouteRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
public class HttpServer {

    private Javalin app;

    private static class Role implements RouteRole {
        public final String role;

        private Role(String role) {
            this.role = role;
        }
    }

    private static final RouteRole[] ROLES = {new Role("admin"),new Role("user")};
    private static final String[] ROLES_PATH_PREFIX = {"/api/admin","/api/user"};
    private static final String SESSION_ROLE_KEY = "session_role_key";

    private static final String[] WRITABLE_PATH_PREFIX = {"/login","/401","/doLogin","/logout"};


    public HttpServer() {
        app = Javalin.create(javalinConfig -> javalinConfig.showJavalinBanner = false);
        log.info("启动Web");
        app.start(Integer.parseInt(Continer.get(ConfigLoader.class).get(ConfigLoader.KeyEnum.WEBPORT)));
        initUI(app);
        initializationAuth(app);
        initAdminApi(app);
    }

    public void stop(){
        app.stop();
    }

    private void initializationAuth(Javalin jl){
        jl.updateConfig(cfg->{
            cfg.accessManager((handler, ctx, routeRoles)->{
                if (Arrays.stream(WRITABLE_PATH_PREFIX).anyMatch(ctx.path()::startsWith)) {
                    handler.handle(ctx);
                    return;
                }
                Object role = ctx.sessionAttribute(SESSION_ROLE_KEY);
                if (role == null){
                    String accept = ctx.header("accept");
                    if (accept != null && accept.contains(ContentType.HTML)){
                        ctx.redirect("/401");
                    }else {
                        ctx.result("Unauthorized");
                    }
                    return;
                }
                for (int i = 0; i < ROLES_PATH_PREFIX.length; i++) {
                    if (ctx.path().startsWith(ROLES_PATH_PREFIX[i])){
                        if (!routeRoles.contains(role))
                        {
                            String accept = ctx.header("accept");
                            if (accept != null && accept.contains(ContentType.HTML)){
                                ctx.redirect("/401");
                            }else {
                                ctx.result("Unauthorized");
                            }
                            return;
                        }
                        break;
                    }
                }
                handler.handle(ctx);
            });
        });
    }

    private void initUI(Javalin jl){
        jl.get("/login", ctx -> {
            ctx.contentType(ContentType.TEXT_HTML);
            ctx.result(loadResource("login.html"));
        });
        jl.get("/401", ctx -> {
            ctx.contentType(ContentType.TEXT_HTML);
            ctx.result(loadResource("401.html"));
        });

        jl.get("/index", ctx -> {
            ctx.contentType(ContentType.TEXT_HTML);
            ctx.result(loadResource("index.html"));
        });

        jl.get("/", ctx -> {
            ctx.redirect("/index");
        });

        jl.post("/doLogin",ctx->{
            String username = ctx.formParam("username");
            String password = ctx.formParam("password");
            if (Continer.get(ConfigLoader.class).get(ConfigLoader.KeyEnum.U).equals(username) && Continer.get(ConfigLoader.class).get(ConfigLoader.KeyEnum.P).equals(password)) {
                ctx.sessionAttribute(SESSION_ROLE_KEY,ROLES[0]);
                ctx.redirect("/index");
            }else {
                ctx.result("失败");
            }
        });

        jl.get("/logout",ctx->{
            ctx.sessionAttribute(SESSION_ROLE_KEY,null);
            ctx.redirect("/401");
        });

    }

    private void initAdminApi(Javalin jl){
        jl.get(ROLES_PATH_PREFIX[0]+"/clientList",ctx->{
            ctx.result("123");
        },ROLES[0]);
    }


    private String loadResource(String name)
    {
        String html = "";
        try(InputStream ras = this.getClass().getResourceAsStream("/web/" + name)) {
            html = new String(ras.readAllBytes());
        }catch (Exception ignored)
        {}
        return html;
    }

}
