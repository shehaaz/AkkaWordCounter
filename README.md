###Implementation of [Concurrency and Fault Tolerance Made Easy: An Akka Tutorial with Examples](https://www.toptal.com/scala/concurrency-and-fault-tolerance-made-easy-an-intro-to-akka)

	Using Akka Actors this reads all text files in a directory and counts the number of words in each file.

	This is an Intellij SBT project, so you can clone it and play around. I hope this helps someone! 😀

Example output:

    Stream Complete: 2.txt Total Number of Words: 13084 Total Time: 148ms
    Stream Complete: 4.txt Total Number of Words: 13084 Total Time: 113ms
    Stream Complete: 8.txt Total Number of Words: 9100 Total Time: 107ms
    Stream Complete: 5.txt Total Number of Words: 13084 Total Time: 160ms
    Stream Complete: 6.txt Total Number of Words: 9100 Total Time: 99ms
    Stream Complete: 7.txt Total Number of Words: 9100 Total Time: 109ms
    Stream Complete: 3.txt Total Number of Words: 13084 Total Time: 238ms
    Stream Complete: 10.txt Total Number of Words: 26168 Total Time: 306ms
    Stream Complete: 9.txt Total Number of Words: 1256064 Total Time: 1267ms
    Stream Complete: 1.txt Total Number of Words: 1256064 Total Time: 1283ms
	


![Diagram](http://i.imgur.com/qjs0IJD.jpg)


####**Update**: Don't create a new ActorSystem for each file.

[Thanks Reddit User mmccaskill](https://www.reddit.com/r/scala/comments/4d3mww/example_using_akka_actors_read_all_text_files_in/d1o01ez) for pointing it out.


* The solution was to [pass in the ActorSystem as a parameter](https://github.com/shehaaz/AkkaWordCounter/blob/master/src/main/scala/wordcounter/AkkaWordCounter.scala#L129). 

* [Don't name the Listener and Router Actors let Akka do it](https://github.com/shehaaz/AkkaWordCounter/blob/master/src/main/scala/wordcounter/AkkaWordCounter.scala#L133), so it will be unique 

* [Keep count of the number of files processed](https://github.com/shehaaz/AkkaWordCounter/commit/cd41a809fea38ab3215f91c892ec08245e98a84d?diff=split#diff-abd6ad6b59a22ce5db6e19ad0dd7cfafR144), so you can terminate the ActorSystem when everything finishes.


Read more here:
[Do I need to re-use the same Akka ActorSystem or can I just create one every time I need one?]
(http://stackoverflow.com/questions/10396552/do-i-need-to-re-use-the-same-akka-actorsystem-or-can-i-just-create-one-every-tim)

####[Messages are sent to an Actor through one of the following methods.](http://doc.akka.io/docs/akka/2.4.1/scala/actors.html#Send_messages)

* `!` means “fire-and-forget”, e.g. send a message asynchronously and return immediately. Also known as tell.
* `?` sends a message asynchronously and returns a Future representing a possible reply. Also known as ask
