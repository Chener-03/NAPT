package xyz.chener.ext.napt.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import xyz.chener.ext.napt.server.entity.ClientItem;

import java.util.Map;

public interface ClientItemMapper extends BaseMapper<ClientItem> {

    Map<String, Object> getAllTables();

    int add(@Param("clientUid") String clientUid,@Param("address") String address
            ,@Param("port") int port,@Param("traffic") Long traffic);

}
