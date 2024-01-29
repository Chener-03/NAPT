package xyz.chener.napt.server.http.entity

import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock

class SyncLockPayload(val lock:Lock, val condition: Condition,var result:String? = null) {
}