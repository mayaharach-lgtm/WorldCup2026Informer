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
#include <algorithm>
#include <fstream>

struct EventLog {
    int time;
    bool before_halftime;
    std::string name;
    std::string description;
};

struct GameData {
    std::string team_a;
    std::string team_b;
    std::map<std::string, std::string> general_stats;
    std::map<std::string, std::string> team_a_stats;
    std::map<std::string, std::string> team_b_stats;
    std::vector<EventLog> events;
};      


class StompProtocol {
private:
    std::atomic<bool> &shouldTerminate;
    int subCounter;
    int receiptCounter;
    std::string currentUser;
    std::map<std::string, int> channelToSubId; 
    std::map<int, std::string> receiptToSuccessMsg; 
    std::map<std::pair<std::string, std::string>, GameData> gameRecords;
    

    static inline std::string trim(std::string s) {
        const char* ws = " \t\r\n";
        size_t b = s.find_first_not_of(ws);
        if (b == std::string::npos) return "";
        size_t e = s.find_last_not_of(ws);
        return s.substr(b, e - b + 1);
    }

    static inline bool startsWith(const std::string& s, const std::string& pref) {
        return s.rfind(pref, 0) == 0;
    }

    static bool splitKeyVal(const std::string& line, std::string& key, std::string& val) {
        size_t pos = line.find(':');
        if (pos == std::string::npos) return false;
        key = trim(line.substr(0, pos));
        val = trim(line.substr(pos + 1));
        return true;
    }


public:
    StompProtocol(std::atomic<bool> &terminate) : 
        shouldTerminate(terminate), subCounter(0), receiptCounter(0) {}

    

    std::string createConnectFrame(std::string host, std::string user, std::string pass) {
        currentUser = user;
        return "CONNECT\naccept-version:1.2\nhost:" + host + "\nlogin:" + user + "\npasscode:" + pass + "\n\n";
    }

    std::string createDisconnectFrame() {
        std::string frame = "DISCONNECT\n";
        frame += "receipt:" + std::to_string(receiptCounter) + "\n";
        frame += "\n"; 
        return frame;
    }

    std::vector<std::string> processInput(std::string input) {
        std::vector<std::string> frames;
        std::stringstream ss(input);
        std::string cmd; ss >> cmd;
        if (cmd == "join") {
            // SUBSCRIBE 
            std::string game; ss >> game;
            if (channelToSubId.count(game)) {
                std::cout << "Already subscribed to channel " << game << std::endl;
            } else {
                int rid = receiptCounter++;
                int sid = subCounter++;
                channelToSubId[game] = sid;
                receiptToSuccessMsg[rid] = "Joined channel " + game; 
                frames.push_back("SUBSCRIBE\ndestination:/" + game + "\nid:" + std::to_string(sid) + "\nreceipt:" + std::to_string(rid) + "\n\n");
            }
        }
        else if (cmd == "exit") {
            // UNSUBSCRIBE command 
            std::string game; ss >> game;
            if(channelToSubId.count(game)){
                int sid = channelToSubId[game];
                int rid = receiptCounter++;
                receiptToSuccessMsg[rid] = "Exited channel " + game; 
                frames.push_back("UNSUBSCRIBE\nid:" + std::to_string(sid) + "\nreceipt:" + std::to_string(rid) + "\n\n");
            }
        }
        else if (cmd == "report") {
            // SEND command for each event in the JSON file 
            std::string filePath; ss >> filePath;
            try {
                names_and_events nne = parseEventsFile(filePath); 
                for (const Event& e : nne.events) {
                    frames.push_back(createSendFrame(e, nne.team_a_name, nne.team_b_name));
                }
            } catch (...) {
                std::cout << "Error parsing file: " << filePath << std::endl;
            }
        }
        else if (cmd == "logout") {
            // DISCONNECT command(graceful shutdown) 
            int rid = receiptCounter++;
            receiptToSuccessMsg[rid] = "logout";
            frames.push_back("DISCONNECT\nreceipt:" + std::to_string(rid) + "\n\n");
        }
        else if (cmd == "summary") {
            std::string gameName, user, fileName;
            ss >> gameName >> user >> fileName;
            
            auto it = gameRecords.find({gameName, user});
            if (it != gameRecords.end()) {
                std::ofstream outFile(fileName, std::ios::trunc);
                const auto& data = it->second;

                outFile << data.team_a << " vs " << data.team_b << "\n";
                outFile << "Game stats:\nGeneral stats:\n";
                for (auto const& [k, v] : data.general_stats) 
                outFile << k << ": " << v << "\n";
                outFile << data.team_a << " stats:\n";
                for (auto const& [k, v] : data.team_a_stats) 
                outFile << k << ": " << v << "\n";
                outFile << data.team_b << " stats:\n";
                for (auto const& [k, v] : data.team_b_stats)
                    outFile << k << ": " << v << "\n";
                
                outFile << "Game event reports:\n";
                auto sortedEvents = data.events;
               std::sort(sortedEvents.begin(), sortedEvents.end(),
                [](const EventLog& a, const EventLog& b) {
                    if (a.before_halftime != b.before_halftime)
                        return a.before_halftime > b.before_halftime; 
                    return a.time < b.time;
                });
                for (const auto& e : sortedEvents) {
                    outFile << e.time << "-" << e.name << ":\n";
                    outFile << e.description << "\n";
                }
                outFile.close();
                std::cout << "Summary generated in " << fileName << std::endl;
            } 
            else {
                std::cout << "No data found for summary." << std::endl;
            }
        
        }
        return frames; 
    }

    
    static void appendMap(std::string& out, const std::map<std::string,std::string>& m) {
        for (const auto& [k,v] : m) {
            out += k + ":" + v + "\n";
        }
    }
    // Formats a SEND frame body based on requirements
    std::string createSendFrame(const Event& e, std::string teamA, std::string teamB) {
        std::string dest = teamA + "_" + teamB;
        std::string body;
        body += "user:" + currentUser + "\n";
        body += "team a:" + teamA + "\n";
        body += "team b:" + teamB + "\n";
        body += "event name:" + e.get_name() + "\n";
        body += "time:" + std::to_string(e.get_time()) + "\n";
        body += "general game updates:\n";
        appendMap(body, e.get_game_updates());
        body += "team a updates:\n";
        appendMap(body, e.get_team_a_updates());
        body += "team b updates:\n";
        appendMap(body, e.get_team_b_updates());
        body += "description:\n";
        body += e.get_discription();
        body += "\n";
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
            // Parse headers until blank line; capture destination
            std::stringstream msg(frame);
            std::string line;
            std::getline(msg, line); // "MESSAGE"

            std::string destination; // e.g. "/usa_mexico"
            while (std::getline(msg, line)) {
                if (line == "\r" || line.empty()) break; // end headers
                std::string k, v;
                if (splitKeyVal(line, k, v)) {
                    if (k == "destination") destination = v;
                }
            }

            // normalize gameName from destination
            std::string gameName = destination;
            gameName = trim(gameName);
            if (!gameName.empty() && gameName[0] == '/')
                gameName = gameName.substr(1);

            // Body parsing
            std::string reporter, teamA, teamB, eventName, description;
            int eventTime = 0;
            bool beforeHalftime = false;

            enum Section { NONE, GENERAL, TEAM_A, TEAM_B, DESCRIPTION };
            Section section = NONE;

            std::map<std::string, std::string> genUp, aUp, bUp;

            while (std::getline(ss, line)) {
                line = trim(line);
                if (line.empty()) continue;

                // Switch sections (match assignment-like labels)
                if (line == "general game updates :" || line == "general game updates:") {
                    section = GENERAL; 
                    continue;
                }
                if (line == "team a updates :" || line == "team a updates:") {
                    section = TEAM_A;
                    continue;
                }
                if (line == "team b updates :" || line == "team b updates:") {
                    section = TEAM_B;
                    continue;
                }
                if (line == "description :" || line == "description:") {
                    section = DESCRIPTION;
                    continue;
                }

                if (section == DESCRIPTION) {
                    if (!description.empty()) description += "\n";
                    description += line;
                    continue;
                }

                // Top fields (match assignment-like labels)
                // Accept both "key : val" and "key: val"
                std::string key, val;
                if (splitKeyVal(line, key, val)) {
                    if (key == "user") { reporter = val; continue; }
                    if (key == "team a") { teamA = val; continue; }
                    if (key == "team b") { teamB = val; continue; }
                    if (key == "event name") { eventName = val; continue; }
                    if (key == "time") { 
                        try { eventTime = std::stoi(val); } catch (...) { eventTime = 0; }
                        continue;
                    }

                    // Inside sections
                    if (section == GENERAL) {
                        genUp[key] = val;
                        if (key == "before halftime") {
                            // val could be "true"/"false"/"1"/"0"
                            std::string low = val;
                            std::transform(low.begin(), low.end(), low.begin(), ::tolower);
                            beforeHalftime = (low == "true" || low == "1");
                        }
                        continue;
                    }
                    if (section == TEAM_A) { aUp[key] = val; continue; }
                    if (section == TEAM_B) { bUp[key] = val; continue; }
                }

                // Description: take as raw lines (even if they contain ':')
                if (section == DESCRIPTION) {
                    if (!description.empty()) description += "\n";
                    description += line;
                }
            }

            // Update gameRecords (key = (gameName, reporter))
            if (!gameName.empty() && !reporter.empty()) {
                GameData& g = gameRecords[{gameName, reporter}];

                if (g.team_a.empty()) g.team_a = teamA;
                if (g.team_b.empty()) g.team_b = teamB;

                for (const auto& [k, v] : genUp) g.general_stats[k] = v;
                for (const auto& [k, v] : aUp)   g.team_a_stats[k] = v;
                for (const auto& [k, v] : bUp)   g.team_b_stats[k] = v;

                g.events.push_back(EventLog{eventTime, beforeHalftime, eventName, description});
            }
    }
        else if (frame.find("ERROR") == 0) {
            // Print error and close connection 
            std::cout << "Error: " << frame << std::endl;
            shouldTerminate = true;
        }
    }
};

