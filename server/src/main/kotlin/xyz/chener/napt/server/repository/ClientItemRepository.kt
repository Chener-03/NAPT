package xyz.chener.napt.server.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import xyz.chener.napt.server.entity.ClientItem


interface ClientItemRepository : JpaRepository<ClientItem,Long>, JpaSpecificationExecutor<ClientItem> {


    @Query("select e from ClientItem e where e.clientUid = :uid")
    fun findClientItemByClientUid(uid:String):List<ClientItem>

}