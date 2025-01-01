// src/main/scala/blockchain/Upnp/Cling.scala

package blockchain.Upnp

import org.fourthline.cling.model.message.header.STAllHeader
import org.fourthline.cling.model.meta.{LocalDevice, RemoteDevice}
import org.fourthline.cling.registry.{Registry, RegistryListener}
import org.fourthline.cling.support.igd.PortMappingListener
import org.fourthline.cling.support.model.PortMapping
import org.fourthline.cling.UpnpService
import org.fourthline.cling.UpnpServiceImpl

object Cling {
  // UPnP discovery is asynchronous, we need a callback
  val listener: RegistryListener = new RegistryListener() {

    def remoteDeviceDiscoveryStarted(registry: Registry,  device: RemoteDevice): Unit = {
      println("Discovery started: " + device.getDisplayString())
    }

    def remoteDeviceDiscoveryFailed(registry: Registry, device: RemoteDevice, ex: Exception): Unit = {
      println("Discovery failed: " + device.getDisplayString() + " => " + ex)
    }

    def remoteDeviceAdded(registry: Registry,  device: RemoteDevice): Unit = {
      println("Remote device available: " + device.getDisplayString())
    }

    def remoteDeviceUpdated(registry: Registry,  device: RemoteDevice): Unit = {
      println("Remote device updated: " + device.getDisplayString())
    }

    def remoteDeviceRemoved(registry: Registry,  device: RemoteDevice): Unit = {
      println("Remote device removed: " + device.getDisplayString())
    }

    def localDeviceAdded(registry: Registry,  device: LocalDevice): Unit = {
      println("Local device added: " + device.getDisplayString())
    }

    def localDeviceRemoved(registry: Registry,  device: LocalDevice): Unit = {
      println("Local device removed: " + device.getDisplayString())
    }

    def beforeShutdown(registry: Registry): Unit = {
      println("Before shutdown, the registry has devices: " + registry.getDevices().size())
    }

    def afterShutdown(): Unit = {
      println("Shutdown of registry complete!")
    }
  }
}
