# Concurrent Duplex Stream Testing

This project contains some Java classes to aid in performing testing on duplex
(simultaneous two-way) "streams" in a synchronous way.

For example, this system would allow you to create some JUnit test cases to
directly test the implementation of a protocol over TCP. Or test the
implementation of a protocol at a higher level.

The library is designed to be used for when the input and output of the streams
are handled in different threads, and it brings a shynchronous interface with
which you can test it.

Example Code:

    // Taken from examples.TelnetServer

    CDSTester<String, String> tester = new CDSTester<String, String>(2000);

    tester.addInputWrite("Hello");
    tester.addInputWrite("How are you today?");

    tester.addOutputRead("Good");

    tester.addInputWrite("Great");

    tester.addOutputRead("Yourself?");

    tester.addInputWrite("Great, thanks for asking!");

The "inputs" and "output" can be of any type you like, and can even be
different.

## Terminology

Input and Output have a consistent meaning throughout this project, and one
which may be counter-intuitive.

* **Input:** the input of the stream endpoint, in relation to the stream. The
  tester class will **write to** the **input** of the stream during testing.
* **Output:** the output of the stream, in relation to the stream. The tester
  class will wait for **reads from** the **output** of the stream.

*Note: this is the reverse of how we usually treat input and output, with
respect to a client or server, not the "wires" (the stream).*

