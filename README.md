Proposed level of achievement: Apollo 11

Motivation
More often than not, it may be difficult to navigate indoors especially with GPS as it only shows a dot on a map. It may also be inconvenient to ask for directions in a building as there may not be people who are available every time you need help. Furthermore, it may be an ugly sight to be seen panicking while trying to find your way to your intended destination especially when turning up for the first job interview or to secure a deal as it might leave a negative impression on your potential employers or partners. 
Wouldn't it help if there was some sort of reference to indicate your current location and intended destination? That is exactly why we chose to embark on this project. We aim to locate the users' position using WiFi localisation technology and clearly indicate it on our android application alongside the users' intended destination.

Aim
We hope to be able to accurately locate the user using WiFi localisation techniques and display the starting and ending locations of the user in our android application.

User stories:
1.	 Being the first time in a building that I am not very familiar with, I would love to have a guide to how I should proceed to navigate to my intended location.
2.	 When I am trying to find my way in a building, I would like realtime reference points to know if I am heading in the correct direction.
3.	 I would want to be confident of being able to find my way if I am left on my own devices to navigate elsewhere.

Scope of project:
Our Android application aims to provide an intuitive interface for users to be able to get a grasp of both their current location and the location of the destination they wish to be. 
A database will be used to store our datasets that are mainly used in locating algorithms to map out the current location of the user.

Features to be completed by second milestone:
1.	Inclusion of Received Signal Strength Indicator (RSSI) capabilities
o	Allows our application to capture the relative signal strengths of nearby WiFi access points
o	Allows realtime updates of RSSI
2.	Inclusion of a locating algorithm
o	Mathematical algorithms allow our application to be able to triangulate the position of the user based on the realtime RSSI information
3.	Inclusion of a database into our application
o	Data that contains points to serve as anchors for our mathematical algorithms to be able to triangulate the users' position
Features to be completed by third milestone:
1.	Refinements based on milestone one feedback
o	We aim to make the application more useful and user friendly based on the feedbacks
2.	Improvement of the accuracy of locating algorithms
o	It may be a challenge to very accurately locate the user and hence a lot of calibration is needed

Core Features:
1.	Storing of original dataset in a database
o	We need to store the original dataset for comparing realtime values to correctly locate the user
2.	Locating algorithm to locate user in a building
o	This feature takes in realtime values and compares with datasets to locate the user
3.	RSSI scanner to scan for RSSI values
o	This step is essential for the application to collect realtime values for locating purposes

How are we different from existing implementations?
1.	WiFi Indoor Localization (Google Play)
o	The application requires the user to manually input his/her current location based on a floor plan submitted by the user which counteracts the initial intention of WiFi localisation.
o	Our application takes in the floor plan and generates the user location after a WiFi scan
2.	Anyplace Indoor Service
o	This application makes use of GPS technology and detailed floor plans to carry out indoor localisation
o	Our application makes use of WiFi technology and detailed floor plans to carry out indoor localisation
 
Project Summary
All in all, we aim to be able to cater to a general group of audience who needs to have a tool to navigate through a building. Our application will be created to have a user friendly and intuitive interface to allow people who may not be very technological savvy to be able to understand the application's usage and flow. The main backbone of our application would be a map feed with the building of interest, followed by two points on the map feed; namely the starting and ending locations. 

