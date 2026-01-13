#pragma once
#include "../include/ConnectionHandler.h"
#pragma once
#include <string>
#include <map>
#include <vector>
#include <atomic>
#include <sstream>
#include <iostream>
#include "../include/event.h"

class StompProtocol {
private:
    std::atomic<bool> &shouldTerminate;
    int subCounter;
    int receiptCounter;
    std::string currentUser;
    std::map<std::string, int> channelToSubId; 
    std::map<int, std::string> receiptToSuccessMsg; 


public:
    StompProtocol(std::atomic<bool> &terminate) : 
        shouldTerminate(terminate), subCounter(0), receiptCounter(0) {}

    // Generates a "connect" frame 
    std::string createConnectFrame(std::string host, std::string user, std::string pass) {
        currentUser = user;
        return "CONNECT\naccept-version:1.2\nhost:" + host + "\nlogin:" + user + "\npasscode:" + pass + "\n\n";
    }

    // Parses keyboard input and returns a vector of STOMP frames to send
    std::vector<std::string> processInput(std::string input) {
        std::vector<std::string> frames;
        std::stringstream ss(input);
        std::string cmd; ss >> cmd;

        if (cmd == "join") {
            // SUBSCRIBE command 
            std::string game; ss >> game;
            int rid = receiptCounter++;
            int sid = subCounter++;
            channelToSubId[game] = sid;
            receiptToSuccessMsg[rid] = "Joined channel " + game; 
            frames.push_back("SUBSCRIBE\ndestination:/" + game + "\nid:" + std::to_string(sid) + "\nreceipt:" + std::to_string(rid) + "\n\n");
        }
        else if (cmd == "exit") {
            // UNSUBSCRIBE command 
            std::string game; ss >> game;
            int sid = channelToSubId[game];
            int rid = receiptCounter++;
            receiptToSuccessMsg[rid] = "Exited channel " + game; 
            frames.push_back("UNSUBSCRIBE\nid:" + std::to_string(sid) + "\nreceipt:" + std::to_string(rid) + "\n\n");
        }
        else if (cmd == "report") {
            // SEND command for each event in the JSON file 
            std::string filePath; ss >> filePath;
            names_and_events nne = parseEventsFile(filePath); // Using provided parser 
            for (const Event& e : nne.events) {
                frames.push_back(createSendFrame(e, nne.team_a_name, nne.team_b_name));
            }
        }
        else if (cmd == "logout") {
            // DISCONNECT command for graceful shutdown 
            int rid = receiptCounter++;
            receiptToSuccessMsg[rid] = "logout";
            frames.push_back("DISCONNECT\nreceipt:" + std::to_string(rid) + "\n\n");
        }
        else if (cmd == "summary") {
            // Local operation: print saved updates to file 
            // Implement logic to read from local storage and write to file
        }
        return frames;
    }

    // Formats a SEND frame body based on requirements
    std::string createSendFrame(const Event& e, std::string teamA, std::string teamB) {
        std::string dest = teamA + "_" + teamB;
        std::string body = "user: " + currentUser + "\nteam a: " + teamA + "\nteam b: " + teamB + 
                           "\nevent name: " + e.get_name() + "\ntime: " + std::to_string(e.get_time()) + 
                           "\ngeneral game updates:\n";
        // Add map contents for updates as per spec 
        return "SEND\ndestination:/" + dest + "\n\n" + body;
    }

    // Processes frames received from the server
    void processResponse(std::string frame) {
        std::stringstream ss(frame);
        std::string line;
        std::getline(ss, line);
        if (frame.find("CONNECTED") == 0) {
            std::cout << "Login successful" << std::endl; 
        } 
        else if (frame.find("RECEIPT") == 0) {
            // Extract receipt-id and print success message or terminate if logout 
            while (std::getline(ss, line) && line != "") {
                if (line.find("receipt-id:") == 0) {
                    int rid = std::stoi(line.substr(11)); 
                    if (receiptToSuccessMsg.count(rid)) {
                        std::string msg = receiptToSuccessMsg[rid];
                        std::cout << msg << std::endl;
                        if (msg == "logout") {
                            shouldTerminate = true;
                        }
                    }
                }
            }
        }
        else if (frame.find("MESSAGE") == 0) {
            // Parse message and update local GameState for 'summary' 
            std::cout << "--- New Message Received ---\n" << frame << std::endl;
        }
        else if (frame.find("ERROR") == 0) {
            // Print error and close connection 
            std::cout << "Error: " << frame << std::endl;
            shouldTerminate = true;
        }
    }
};