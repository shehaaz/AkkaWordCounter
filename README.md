Implementation of [Concurrency and Fault Tolerance Made Easy: An Akka Tutorial with Examples](https://www.toptal.com/scala/concurrency-and-fault-tolerance-made-easy-an-intro-to-akka)

Using Akka Actors this reads all text files in a directory and counts the number of words in each file.

Example output:

`$>`

	Stream Complete: 10.txt Total Number of Words: 9100 Total Time: 92ms
	Stream Complete: 1.txt Total Number of Words: 13084 Total Time: 184ms
	Stream Complete: 3.txt Total Number of Words: 13084 Total Time: 107ms
	Stream Complete: 6.txt Total Number of Words: 9100 Total Time: 109ms
	Stream Complete: 4.txt Total Number of Words: 13084 Total Time: 161ms
	Stream Complete: 7.txt Total Number of Words: 9100 Total Time: 134ms
	Stream Complete: 5.txt Total Number of Words: 13084 Total Time: 198ms
	Stream Complete: 2.txt Total Number of Words: 13084 Total Time: 322ms
	Stream Complete: 8.txt Total Number of Words: 9100 Total Time: 31ms
	Stream Complete: 9.txt Total Number of Words: 9100 Total Time: 27ms
	

![Diagram](http://i.imgur.com/QI58jTd.jpg)
