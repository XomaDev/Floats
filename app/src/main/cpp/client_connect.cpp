#include <iostream>
#include <string>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <jni.h>
#include <fstream>
#include <fcntl.h>


constexpr uint32_t MOD_ADLER = 65521;
constexpr int BUFFER_SIZE = 1000000;

jstring receiveContentSocket(JNIEnv *env, jobject callback, jstring output, jstring host, jint port,
                             bool retry) {
   const char *hostStr = env->GetStringUTFChars(host, nullptr);
   const char *outputPath = env->GetStringUTFChars(output, nullptr);

   std::string hostName(hostStr);
   std::string outputPathStr(outputPath);

   int portNumber = static_cast<int>(port);

   // Create a socket
   int sock = socket(AF_INET, SOCK_STREAM, 0);
   if (sock == -1)
      return env->NewStringUTF("Failed to create socket.");


   // Set up the server address
   sockaddr_in serverAddress{};
   serverAddress.sin_family = AF_INET;
   serverAddress.sin_port = htons(portNumber);

   if (inet_pton(AF_INET, hostName.c_str(), &(serverAddress.sin_addr)) <= 0)
      return env->NewStringUTF("Failed to set up server address.");


   // Connect to the server
   if (connect(sock, (struct sockaddr *) &serverAddress, sizeof(serverAddress)) < 0) {
      if (retry) {
         // we will retry connection again, the server may not be ready yet
         // 1 second -> 1000000 seconds
         usleep(1000000);

         return receiveContentSocket(
                 env,
                 callback,
                 output,
                 host,
                 port,
                 false);
      }
      return env->NewStringUTF("Failed to connect to the server.");
   }


   // Open the output file
   int outputFile = open(outputPathStr.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
   if (outputFile == -1)
      return env->NewStringUTF("Failed to open output file.");

   jclass clazz = env->GetObjectClass(callback);
   env->CallVoidMethod(callback, env->GetMethodID(clazz, "onStart", "()V"));

   // Read and save data to the output file
   char *buffer = new char[BUFFER_SIZE];

   ssize_t nRead = 0;
   ssize_t bytesRead;

   jmethodID methodId = env->GetMethodID(clazz, "update", "(I)V");

   char last8Bytes[8];
   memset(last8Bytes, 0, sizeof(last8Bytes));
   bool lastRead = false;


   uint32_t a = 1;
   uint32_t b = 0;

   while ((bytesRead = read(sock, buffer, BUFFER_SIZE)) > 0) {
      if (lastRead) {
         // process the previous last 8 bytes
         for (char last8Byte: last8Bytes) {
            a += last8Byte;
            if (a >= MOD_ADLER)
               a -= MOD_ADLER;

            b += a;
            if (b >= MOD_ADLER)
               b -= MOD_ADLER;
         }
      }

      memcpy(last8Bytes, buffer + (bytesRead - 8), 8);

      for (size_t i = 0, len = bytesRead - 8; i < len; ++i) {
         a += buffer[i];
         if (a >= MOD_ADLER)
            a -= MOD_ADLER;

         b += a;
         if (b >= MOD_ADLER)
            b -= MOD_ADLER;
      }

      ssize_t bytesWritten = write(outputFile, buffer, bytesRead);
      if (bytesWritten != bytesRead) {
         close(outputFile);
         close(sock);
         return env->NewStringUTF("Failed to write to output file.");
      }

      nRead += bytesRead;
      env->CallVoidMethod(callback, methodId, nRead);
      lastRead = true;
   }

   uint32_t receivedChecksum = (b << 16) | a;

   uint64_t adlerSumCheckCompare =
           (uint64_t) (last8Bytes[0] & 0xFF) << 56 |
           (uint64_t) (last8Bytes[1] & 0xFF) << 48 |
           (uint64_t) (last8Bytes[2] & 0xFF) << 40 |
           (uint64_t) (last8Bytes[3] & 0xFF) << 32 |
           (uint64_t) (last8Bytes[4] & 0xFF) << 24 |
           (uint64_t) (last8Bytes[5] & 0xFF) << 16 |
           (uint64_t) (last8Bytes[6] & 0xFF) << 8 |
           (uint64_t) (last8Bytes[7] & 0xFF) << 0;

   // Close the output file
   close(outputFile);

   // Close the socket
   close(sock);
   delete[] buffer;

   env->ReleaseStringUTFChars(host, hostStr);
   env->ReleaseStringUTFChars(output, outputPath);

   if (adlerSumCheckCompare != receivedChecksum) {
      return env->NewStringUTF(("Adler Sum Mismatched , [" +
                                std::to_string(adlerSumCheckCompare) + ", " +
                                std::to_string(receivedChecksum) + "]").c_str());
   }

   return env->NewStringUTF("successful");
}

extern "C"
JNIEXPORT jstring
Java_com_baxolino_apps_floats_core_NativeInterface_connectToHost(JNIEnv *env, jobject thiz,
                                                                 jobject callback, jstring output,
                                                                 jstring host, jint port) {
   return receiveContentSocket(
           env,
           callback,
           output,
           host,
           port,
           true);
}
