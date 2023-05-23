package xyz.chener.ext.napt.server.core;


import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import xyz.chener.ext.napt.server.entity.ClientItem;
import xyz.chener.ext.napt.server.mapper.ClientItemMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.JarURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Slf4j
public class StrongStarter {

    private DruidDataSource dataSource = null;

    private SqlSessionFactory sqlSessionFactory = null;

    public boolean start() {
        log.info("启动存储服务");
        try {
            Class.forName("org.h2.Driver");
            dataSource = new DruidDataSource();
            dataSource.setUrl("jdbc:h2:./database");
            dataSource.setUsername("");
            dataSource.setPassword("");
            dataSource.setDriverClassName("org.h2.Driver");

            MybatisConfiguration mbpConfig = new MybatisConfiguration();
            mbpConfig.setMapUnderscoreToCamelCase(true);
            mbpConfig.setUseGeneratedKeys(true);
            GlobalConfig globalConfig = GlobalConfigUtils.getGlobalConfig(mbpConfig);
            globalConfig.setSqlInjector(new DefaultSqlInjector());
            globalConfig.setIdentifierGenerator(new DefaultIdentifierGenerator());
            globalConfig.setSuperMapperClass(BaseMapper.class);
            this.registryMapperXml(mbpConfig, "mapper/");
            mbpConfig.addMappers("xyz.chener.ext.napt.server.mapper");


            TransactionFactory transactionFactory = new JdbcTransactionFactory();
            Environment environment = new Environment("development", transactionFactory, dataSource);
            mbpConfig.setEnvironment(environment);
            sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(mbpConfig);
            checkTable();

        }catch (Exception e)
        {
            log.error("启动存储服务失败",e);
            return false;
        }
        return true;
    }

    public void stop(){
        if (dataSource!= null)
            dataSource.close();
    }

    public SqlSessionFactory getSqlSessionFactory() {
        return sqlSessionFactory;
    }

    public DruidDataSource getDataSource() {
        return dataSource;
    }

    private void registryMapperXml(MybatisConfiguration configuration, String classPath) throws IOException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> mapper = contextClassLoader.getResources(classPath);
        while (mapper.hasMoreElements()) {
            URL url = mapper.nextElement();
            if (url.getProtocol().equals("file")) {
                String path = url.getPath();
                File file = new File(path);
                File[] files = file.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().endsWith(".xml")){
                            FileInputStream in = new FileInputStream(f);
                            XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(in, configuration, f.getPath(), configuration.getSqlFragments());
                            xmlMapperBuilder.parse();
                            in.close();
                        }
                    }
                }
            } else {
                JarURLConnection urlConnection = (JarURLConnection) url.openConnection();
                JarFile jarFile = urlConnection.getJarFile();
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry jarEntry = entries.nextElement();
                    if (jarEntry.getName().endsWith(".xml")) {
                        InputStream in = jarFile.getInputStream(jarEntry);
                        XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(in, configuration, jarEntry.getName(), configuration.getSqlFragments());
                        xmlMapperBuilder.parse();
                        in.close();
                    }
                }
            }
        }
    }

    private void checkTable() throws IOException, SQLException {
        ClientItemMapper clientItemMapper = StrongStarter.getMapper(ClientItemMapper.class);
        Map<String, Object> allTables = clientItemMapper.getAllTables();
        if (allTables == null){
            try(InputStream stream = this.getClass().getClassLoader().getResourceAsStream("sql/create_table_client_item.sql")) {
                String sql = new String(stream.readAllBytes());
                this.dataSource.getConnection().getConnection().prepareStatement(sql).execute();
            }
        }

    }


    private static Map<Class, Object> mappers = new ConcurrentHashMap<>();

    public static <T> T getMapper(Class<T> clazz){
        if (mappers.containsKey(clazz)) {
            return (T) mappers.get(clazz);
        }else {
            Object mapper = createMapperProxy(clazz);
            mappers.put(clazz,mapper);
            return (T) mapper;
        }
    }


    private static Object createMapperProxy(Class clazz){
        return Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, (proxy, method, args) -> {
            try(SqlSession session = Continer.get(StrongStarter.class).getSqlSessionFactory().openSession()) {
                Object sourceObj = session.getMapper(clazz);
                return method.invoke(sourceObj,args);
            }
        });
    }


}
