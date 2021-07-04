#**HI THERE!**

##Some important things to note please!

>This assignment utilizes Maven as it's build tool. Please open the POM.XML (if you are using IntelliJ)
file to begin viewing this assignment. Otherwise, please find out how to open maven projects based
on your IDE. 

The simulation can be modified on the top of the file section named "Simulation Variables",
in here random amounts can be tweaked to force certain environments to happen for example,
forcing the Ticket Machine to break very early, forcing the Ticket Booth(s) to take a toilet break
for a longer/shorter period of time. Please check it out.

Also there exists a platform dependent issue, where printing out to console is not always time-consistent.
So as stated in the presentation video, sometimes, for example, taking 4 seconds to issue a ticket will print out to console, 
but depending on the nature of the run, sometimes there might be a delay or inconsistency due to 
the IDE or OS or anything else interferring with the console output on the IDE.

###Requirements Met (ALL of Part 1)
- [x] Customers arrive from the West & East entrances randomly every 1,2,3,4 seconds.

- [x] Queue at the Ticket Machine or one (1) of the Ticket Booths.

- [x] 5 people in line, remainder waiting in foyer.

- [x] Ticket Machine, take 4 seconds to issue.

- [x] Ticket Booth(s) take 8 seconds to issue a ticket.

- [x] Once ticket has been possessed, customers move to one (1) out of three (3) departure gates (based on their direction of travel).

- [x] The ticketing machine in not working – thereby requiring any queuing customers to shift to one of the ticketing booths

- [x] One or more ticketing booth staff are on a toilet break.

- [x] The terminal has reached max capacity.
