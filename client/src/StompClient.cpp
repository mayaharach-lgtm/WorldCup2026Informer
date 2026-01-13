#include <iostream>
#include <thread>
#include <atomic>
#include <string>
#include <sstream>
#include <vector>
#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"

int main(int argc, char *argv[]) {
    std::atomic<bool> shouldTerminate(false);
    ConnectionHandler* handler = nullptr;
    StompProtocol protocol(shouldTerminate);
    std::thread socketThread;

    while (!shouldTerminate) {
        std::string line;
        // Read command from standard input 
        if (!std::getline(std::cin, line)) break;
        if (line.empty()) continue;

        std::stringstream ss(line);
        std::string command;
        ss >> command;

        if (command == "login") {
            // checks if already logged in 
            if (handler != nullptr) {
                std::cout << "The client is already logged in, log out before trying again" << std::endl;
                continue;
            }

            std::string hostPort, user, pass;
            ss >> hostPort >> user >> pass;
            
            size_t colonPos = hostPort.find(':');
            if (colonPos == std::string::npos) continue;

            std::string host = hostPort.substr(0, colonPos);
            short port = std::stoi(hostPort.substr(colonPos + 1));

            // start connection 
            handler = new ConnectionHandler(host, port);
            if (!handler->connect()) {
                std::cout << "Could not connect to server" << std::endl;
                delete handler; 
                handler = nullptr; 
                continue;
            }

            // Thread 2: Reading frames from the socket 
            socketThread = std::thread([&]() {
                while (!shouldTerminate) {
                    std::string frame;
                    // Blocking read until a Null character is received 
                    if (handler->getFrameAscii(frame, '\0')) {
                        protocol.processResponse(frame);
                    } else {
                        // Connection lost or closed by server
                        if (!shouldTerminate) {
                            std::cout << "Disconnected from server" << std::endl;
                            shouldTerminate = true;
                        }
                    }
                }
            });

            // Send CONNECT frame after successful socket connection 
            handler->sendFrameAscii(protocol.createConnectFrame("stomp.cs.bgu.ac.il", user, pass), '\0');
        } 
        else {
            // All other commands require being logged in
            if (handler == nullptr) {
                std::cout << "Please log in first" << std::endl;
                continue;
            }
            
            // Translate keyboard command to STOMP frames 
            std::vector<std::string> frames = protocol.processInput(line);
            for (const std::string& f : frames) {
                handler->sendFrameAscii(f, '\0');
            }
        }
    }

    // shutdown-Wait for the network thread to finish
    if (socketThread.joinable()) socketThread.join();
    if (handler) { 
        handler->close(); 
        delete handler; 
    }
    return 0;
}



