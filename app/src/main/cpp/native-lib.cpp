#include <iostream>
#include <string>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <jni.h>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_baxolino_apps_floats_NativeCpp_connectToHost(JNIEnv *env, jobject thiz, jstring host,
                                                      jint port) {
    const char *hostStr = env->GetStringUTFChars(host, nullptr);

    std::string hostName(hostStr);
    int portNumber = static_cast<int>(port);

    // Create a socket
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock == -1) {
        std::cerr << "Failed to create socket." << std::endl;
        return env->NewStringUTF("h");
    }

    // Set up the server address
    sockaddr_in serverAddress{};
    serverAddress.sin_family = AF_INET;
    serverAddress.sin_port = htons(portNumber);
    if (inet_pton(AF_INET, hostName.c_str(), &(serverAddress.sin_addr)) <= 0) {
        std::cerr << "Failed to set up server address." << std::endl;
        return env->NewStringUTF("ha");

    }

    // Connect to the server
    if (connect(sock, (struct sockaddr *) &serverAddress, sizeof(serverAddress)) < 0) {
        std::cerr << "Failed to connect to the server." << std::endl;
        return env->NewStringUTF("haz");
    }

    // Read and discard data
    constexpr int bufferSize = 1024;
    char buffer[bufferSize];
    while (true) {
        ssize_t bytesRead = read(sock, buffer, bufferSize);
        if (bytesRead <= 0) {
            // Error or end of data
            break;
        }

        // Discard the received bytes by not doing anything with them
    }

    // Close the socket
    close(sock);

    env->ReleaseStringUTFChars(host, hostStr);
    return env->NewStringUTF("w");

}