package xyz.chener.ext.napt.server.core;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.security.RouteRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import xyz.chener.ext.napt.server.core.requestNt.RequestNtTcp;
import xyz.chener.ext.napt.server.entity.ClientItem;
import xyz.chener.ext.napt.server.entity.DataFrameCode;
import xyz.chener.ext.napt.server.entity.DataFrameEntity;
import xyz.chener.ext.napt.server.mapper.ClientItemMapper;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
//        initializationAuth(app);
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


    public static final ConcurrentHashMap<String,String> clientConnInfoCache = new ConcurrentHashMap<>();

    private void initAdminApi(Javalin jl){
        jl.get(ROLES_PATH_PREFIX[0]+"/clientList",ctx->{
            int page = Integer.parseInt(ctx.formParam("page"));
            int size = Integer.parseInt(ctx.formParam("size"));
            Page<ClientItem> list = new LambdaQueryChainWrapper<>(StrongStarter.getMapper(ClientItemMapper.class))
                    .page(PageDTO.of(page, size));

        },ROLES[0]);


        jl.get(ROLES_PATH_PREFIX[0]+"/debug/getConnectCache",ctx->{
            StringBuilder sb = new StringBuilder();
            ObjectMapper om = new ObjectMapper();

            sb.append("clientChannel:\n");
            sb.append(om.writerWithDefaultPrettyPrinter().writeValueAsString(ConnectCache.clientChannel));
            sb.append("\n\n");

            sb.append("channelMap:\n");
            ConnectCache.channelMap.forEach((k,v)->{
                sb.append(k).append(" -> ").append(v.channel().remoteAddress()).append("\n");
            });
            sb.append("\n\n");

            sb.append("portStarts:\n");
            ConnectCache.portStarts.forEach((k,v)->{
                sb.append(k).append(" -> ").append("\n");
                v.forEach(dt->{
                    sb.append("    ")
                            .append(dt.getClientAddr()).append(" -> ")
                            .append(dt.getPort()).append(" -> ")
                            .append("IN ")
                            .append(dt.getSpeedLimitHandler() != null ? dt.getSpeedLimitHandler().trafficCounter().currentWrittenBytes() : 0)
                            .append(" :  OUT ")
                            .append(dt.getSpeedLimitHandler() != null ? dt.getSpeedLimitHandler().trafficCounter().currentReadBytes() : 0)
                            .append("\n");
                    sb.append("        Port Connect:\n");
                    if (dt instanceof RequestNtTcp dtt){
                        dtt.getMap().forEach((k1,v1)->{
                            sb.append("        ").append(k1).append(" -> ").append(v1.channel().remoteAddress()).append("\n");
                        });
                    }
                });
            });
            sb.append("\n\n");

            List<String> removeList = new ArrayList<>();
            ConnectCache.channelMap.values().forEach(e->{
                String uid = UUID.randomUUID().toString();
                clientConnInfoCache.put(uid,"NULL");
                removeList.add(uid);
                DataFrameEntity.DataFrame dt = DataFrameEntity.DataFrame.newBuilder()
                        .setCode(DataFrameCode.GET_CLIENT_CONNECTS)
                        .setMessage(uid).build();
                e.channel().writeAndFlush(dt);
            });

            Thread.sleep(1000);
            sb.append("clientConnects:\n");
            clientConnInfoCache.values().forEach(e->{
                sb.append(e).append("\n");
            });
            removeList.forEach(clientConnInfoCache::remove);
            sb.append("\n\n");
            ctx.result(sb.toString());
        });


        jl.post(ROLES_PATH_PREFIX[0]+"/debug/execQuery",ctx->{
            String json = ctx.body();
            ObjectMapper om = new ObjectMapper();
            Map<String,String> map = om.readValue(json, new TypeReference<Map<String, String>>() {});
            if (!map.containsKey("sql")) {
                ctx.result("未找到sql");
                return;
            }

            String sql = map.get("sql");
            DruidDataSource dataSource = Continer.get(DruidDataSource.class);
            try (Connection conn = dataSource.getConnection()){
                Statement statement = conn.createStatement();
                ResultSet resultSet = statement.executeQuery(sql);
                List<Object> res = new ArrayList<>();

                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (resultSet.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object columnValue = resultSet.getObject(i);
                        row.put(columnName, columnValue);
                    }
                    res.add(row);
                }

                ctx.result(om.writerWithDefaultPrettyPrinter().writeValueAsString(res));
            }catch (Exception exception){
                ctx.result(exception.getMessage());
            }
        });

        jl.post(ROLES_PATH_PREFIX[0]+"/debug/execUpdate",ctx->{
            String json = ctx.body();
            ObjectMapper om = new ObjectMapper();
            Map<String,String> map = om.readValue(json, new TypeReference<Map<String, String>>() {});
            if (!map.containsKey("sql")) {
                ctx.result("未找到sql");
                return;
            }

            String sql = map.get("sql");
            DruidDataSource dataSource = Continer.get(DruidDataSource.class);
            try (Connection conn = dataSource.getConnection()){
                Statement statement = conn.createStatement();
                int i = statement.executeUpdate(sql);
                ctx.result("影响行数: " + i + " 行");
            }catch (Exception exception){
                ctx.result(exception.getMessage());
            }
        });

        jl.get(ROLES_PATH_PREFIX[0]+"/debug/flushByUid",ctx -> {
            String uid = ctx.queryParam("uid");
            if (StringUtils.hasText(uid)){
                Continer.get(ClientManager.class).flush(uid);
                ctx.result("OK");
            }else {
                ctx.result("FAIL");
            }
        });


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
