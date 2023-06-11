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

bool wasCancelled = false;

extern "C"
JNIEXPORT void JNICALL
Java_com_baxolino_apps_floats_core_NativeInterface_cancel(JNIEnv *env, jobject thiz) {
   wasCancelled = true;
}

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

   jclass clazz = env->GetObjectClass(callback);

   env->CallVoidMethod(callback,
                       env->GetMethodID(clazz, "debug", "(I)V"), 1);


   // Open the output file
   int outputFile = open(outputPathStr.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
   if (outputFile == -1)
      return env->NewStringUTF("Failed to open output file.");

   env->CallVoidMethod(callback, env->GetMethodID(clazz, "onStart", "()V"));

   // Read and save data to the output file
   char buffer[BUFFER_SIZE];

   ssize_t nRead = 0;
   ssize_t bytesRead;

   jmethodID methodId = env->GetMethodID(clazz, "update", "(I)V");

   char last8Bytes[8];
   memset(last8Bytes, 0, sizeof(last8Bytes));

   uint32_t a = 1;
   uint32_t b = 0;

   int readTotalLastBytes = 0;

   while (!wasCancelled && (bytesRead = read(sock, buffer, BUFFER_SIZE)) > 0) {
      for (int i = 0; i < readTotalLastBytes; i++) {
         char c = last8Bytes[i];

         a += c;
         if (a >= MOD_ADLER)
            a -= MOD_ADLER;

         b += a;
         if (b >= MOD_ADLER)
            b -= MOD_ADLER;
      }

      ssize_t bytesWritten1 = write(outputFile, last8Bytes, readTotalLastBytes);
      if (bytesWritten1 != readTotalLastBytes) {
         close(outputFile);
         close(sock);
         return env->NewStringUTF(("Failed to write to output file." + std::to_string(bytesWritten1)).c_str());
      }

      int min = std::min((int) bytesRead, 8);
      readTotalLastBytes = min;
      memcpy(last8Bytes, buffer + (bytesRead - min), min);

      for (size_t i = 0, len = bytesRead - min; i < len; ++i) {
         a += buffer[i];
         if (a >= MOD_ADLER)
            a -= MOD_ADLER;

         b += a;
         if (b >= MOD_ADLER)
            b -= MOD_ADLER;
      }

      ssize_t bytesWritten = write(outputFile, buffer, bytesRead - min);
      if (bytesWritten != bytesRead - min) {
         close(outputFile);
         close(sock);
         return env->NewStringUTF(("Failed to write to output file. " + std::to_string(bytesWritten)).c_str());
      }

      nRead += bytesRead;
      env->CallVoidMethod(callback, methodId, nRead);
   }
   if (wasCancelled) {
      jmethodID cancelId = env->GetMethodID(clazz, "cancelled", "()V");
      env->CallVoidMethod(callback, cancelId);


      return env->NewStringUTF("Was Cancelled");
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

   env->ReleaseStringUTFChars(host, hostStr);
   env->ReleaseStringUTFChars(output, outputPath);

   if (adlerSumCheckCompare != receivedChecksum) {
      return env->NewStringUTF(("Adler Sum Mismatched , [" +
                                std::to_string(adlerSumCheckCompare) + ", " +
                                std::to_string(receivedChecksum) + "]").c_str());
   }

   return env->NewStringUTF("successful [matches checksum]");
}

extern "C"
JNIEXPORT jstring
Java_com_baxolino_apps_floats_core_NativeInterface_connectToHost(JNIEnv *env, jobject thiz,
                                                                 jobject callback, jstring output,
                                                                 jstring host, jint port) {
   wasCancelled = false;

   return receiveContentSocket(
           env,
           callback,
           output,
           host,
           port,
           true);
}
