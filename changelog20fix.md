# Changelog - WorldReset v2.0fix

### ❤️ Special Thanks
* A huge thank you to **[@yuzzzie](https://github.com/yuzzzie)** for reporting, testing, and verifying the Limbo void fall desynchronization fix!

---

### 🐛 Fixed Bugs in v2.0fix

* 🌌 **Limbo Void Fall Desync Fix:**
  * Fixed a race condition where players falling into the void in Limbo on high-latency or unstable network connections would teleport back to spawn but become frozen and invisible to other players.
  * Switched the void-rescue teleportation to Paper's asynchronous pipeline (`teleportAsync`), allowing the server to decouple the teleport packet from the active damage tick and cleanly eliminate client-server ghost states.
