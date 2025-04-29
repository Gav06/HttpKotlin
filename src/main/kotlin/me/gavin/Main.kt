package me.gavin

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread


// the default path is just index.html
var file: String = "index.html"

// args follow: <port> <file>
fun main(args: Array<String>) {
    // process arguments
    if (args.isEmpty()) {
        println("Usage: java -jar server.jar <port> <file>")
        return
    }
    val port = args[0].toInt()

    if (args.size < 2 || args[1].isEmpty()) {
        println("Using default file: index.html")
    } else {
        println("Using file as root: ${args[1]}")
        file = args[1]
    }


    // create socket
    val socket = ServerSocket(port)
    println("Server listening on port $port")

    while (true) {
        try {
            val client  = socket.accept()
            println("Client connected: ${client.inetAddress.hostAddress}")

            // push our client handling into another thread so we can process more requests :)
            thread {
                handleClient(client)
            }
        } catch (e: Exception) {
            println("Error accepting client connection: ${e.message}")
        }
    }
}

fun sendNotFound(writer: PrintWriter) {
    val body = "<h1>404 Not Found</h1>"
    writer.println("HTTP/1.1 404 Not Found")
    writer.println("Content-Type: text/html; charset=utf-8")
    writer.println("Content-Length: ${body.length}")
    writer.println()
    writer.println(body)
    println("Sent 404 Not Found.")
}

fun handleClient(client: Socket) {
    // read request

    client.inputStream.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            val reqLine = reader.readLine()
            println("Request: $reqLine")

            // optional: print http header
//
//            // read header until empty line
//            var headerLine: String?
//            while (reader.readLine().also { headerLine = it } != null && headerLine!!.isNotEmpty()) {
//                println("Header: $headerLine")
//            }

            val parts = reqLine!!.split(" ")
            val method = parts.getOrNull(0)
            val path = parts.getOrNull(1)

            if (path.isNullOrEmpty()) {
                println("Path is empty")
                return
            }

            // use default file, or requested file
            val fd = if (path == "/") {
                File(file)
            } else {
                File(path.substring(1)).canonicalFile
            }

            if (!fd.exists()) {
                println("File $path does not exist")
                return
            }

            // Write file, or 404
            val stream = fd.inputStream()
            val bytes = stream.readBytes()
            val writer = PrintWriter(OutputStreamWriter(client.getOutputStream()))
            writer.println("HTTP/1.1 200 OK")

            if (method != "GET" && method != "HEAD") {
                sendNotFound(writer)
                return
            }

            // write headers
            val fileName = fd.name
            // determine content type from file suffix, just the basics
            val contentType = when {
                fileName.endsWith(".html") -> "text/html"
                fileName.endsWith(".css") -> "text/css"
                fileName.endsWith(".js") -> "application/javascript"
                fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") -> "image/jpeg"
                fileName.endsWith(".png") -> "image/png"
                fileName.endsWith(".gif") -> "image/gif"
                fileName.endsWith(".svg") -> "image/svg+xml"
                // Add more mime types as needed
                else -> "application/octet-stream" // Default binary type
            }

            writer.println("Content-Type: $contentType")
            writer.println("Content-Length: ${bytes.size}")
            writer.println()
            // write file contents
            writer.println(bytes.toString(Charsets.UTF_8))
            writer.flush()
            writer.close()
        }
    }
}