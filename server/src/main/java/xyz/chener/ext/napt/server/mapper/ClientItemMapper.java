package xyz.chener.ext.napt.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import xyz.chener.ext.napt.server.entity.ClientItem;

import java.util.Map;

public interface ClientItemMapper extends BaseMapper<ClientItem> {

    Map<String,String> getAllTables();

    int add(String clientUid,String address,Long traffic);

}
