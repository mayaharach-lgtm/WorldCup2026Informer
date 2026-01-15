#include <iostream>
#include <thread>
#include <atomic>
#include <string>
#include <sstream>
#include <vector>
#include <memory>
#include <mutex>

#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"

// Reader thread task: shared_ptr copy keeps handler alive safely
void readTask(std::shared_ptr<ConnectionHandler> handler,
              StompProtocol& protocol,
              std::mutex& protocolMtx,
              std::atomic<bool>& connected) {
    while (connected) {
        std::string frame;

        // Blocking read (until '\0')
        if (handler && handler->getFrameAscii(frame, '\0')) {
            // Guard shared protocol state
            std::lock_guard<std::mutex> lock(protocolMtx);
            protocol.processResponse(frame);
        } else {
            if (connected) {
                std::cout << "Disconnected from server." << std::endl;
                connected = false;
            }
            break; // Exit immediately after disconnect/failure
        }
    }
}

// Parse host:port safely (no crash on bad input)
static bool parseHostPort(const std::string& hostPort, std::string& host, short& port) {
    size_t colonPos = hostPort.find(':');
    if (colonPos == std::string::npos || colonPos == 0 || colonPos + 1 >= hostPort.size()) return false;

    host = hostPort.substr(0, colonPos);

    try {
        int p = std::stoi(hostPort.substr(colonPos + 1));
        if (p <= 0 || p > 65535) return false;
        port = static_cast<short>(p);
    } catch (...) {
        return false;
    }
    return true;
}

int main(int argc, char *argv[]) {
    std::atomic<bool> connected(false);
    std::atomic<bool> shouldTerminate(false);

    std::shared_ptr<ConnectionHandler> handler = nullptr;
    std::mutex protocolMtx;
    StompProtocol protocol(shouldTerminate);
    std::thread socketThread;

    // Safe connection shutdown: close first (to release blocking read), then join
    auto safeDisconnect = [&]() {
        if (handler) {
            handler->close(); // release blocking getFrameAscii
        }
        connected = false;

        if (socketThread.joinable()) {
            socketThread.join();
        }

        handler.reset();
    };

    while (!shouldTerminate) {
        std::string line;
        if (!std::getline(std::cin, line)) break;
        if (line.empty()) continue;

        std::stringstream ss(line);
        std::string command;
        ss >> command;

        if (command == "login") {
            if (connected) {
                std::cout << "The client is already logged in." << std::endl;
                continue;
            }

            std::string hostPort, user, pass;
            if (!(ss >> hostPort >> user >> pass)) {
                std::cout << "Usage: login {host:port} {user} {password}" << std::endl;
                continue;
            }

            std::string host;
            short port;
            if (!parseHostPort(hostPort, host, port)) {
                std::cout << "Invalid host:port format" << std::endl;
                continue;
            }

            handler = std::make_shared<ConnectionHandler>(host, port);
            if (!handler->connect()) {
                std::cout << "Connection failed" << std::endl;
                handler.reset();
                continue;
            }

            connected = true;

            // Start reader thread
            socketThread = std::thread(readTask,
                                       handler,
                                       std::ref(protocol),
                                       std::ref(protocolMtx),
                                       std::ref(connected));

            // Send CONNECT
            std::string connectFrame;
            {
                std::lock_guard<std::mutex> lock(protocolMtx);
                connectFrame = protocol.createConnectFrame(host, user, pass);
            }

            if (!handler->sendFrameAscii(connectFrame, '\0')) {
                std::cout << "Failed to send CONNECT. Disconnecting..." << std::endl;
                safeDisconnect();
            }
        }
        else if (command == "logout") {
            if (!connected || !handler) {
                std::cout << "You are not logged in" << std::endl;
                continue;
            }

            std::string logoutFrame;
            {
                std::lock_guard<std::mutex> lock(protocolMtx);
                logoutFrame = protocol.createDisconnectFrame();
            }

            handler->sendFrameAscii(logoutFrame, '\0');

            std::cout << "Waiting for receipt to logout..." << std::endl;
            while (connected) {
                std::this_thread::sleep_for(std::chrono::milliseconds(10));
            }
            safeDisconnect();
            std::cout << "Logged out." << std::endl;
        }
        else if (command == "exit") {
            shouldTerminate = true;
            safeDisconnect();
        }
        else {
            if (!connected || !handler) {
                std::cout << "Please login first" << std::endl;
                continue;
            }

            std::vector<std::string> frames;
            {
                std::lock_guard<std::mutex> lock(protocolMtx);
                frames = protocol.processInput(line);
            }

            for (const std::string& f : frames) {
                if (!handler->sendFrameAscii(f, '\0')) {
                    std::cout << "Send failed. Disconnecting..." << std::endl;
                    safeDisconnect();
                    break;
                }
            }
        }
    }

    safeDisconnect();
    return 0;
}
