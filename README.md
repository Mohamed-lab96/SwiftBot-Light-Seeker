# SwiftBot-Light-Seeker
A Java-based SwiftBot project that autonomously seeks light and avoids obstacles

## Features  
✅ Detects and moves toward light sources  
✅ Avoids obstacles using ultra Sound  
✅ Uses real-time image processing for decision-making  

## How to Run  
1. **Use WinSCP** and connect to the **SwiftBot IP**  
   - (Bot provided by Brunel University of London, use the **Brunel SwiftBot Connects app** to get the IP)  

2. **Open Command Prompt** and run:  
   ```sh
   ssh "Username"@"your.swiftbot.ip.address"

   enter: java -cp SwiftBot-API-5.1.3.jar SearchForLight
