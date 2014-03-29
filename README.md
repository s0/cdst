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

```java
// Taken from examples.TelnetServer

CDSTester<String, String> tester = new CDSTester<String, String>(2000);

tester.addInputWrite("Hello");
tester.addInputWrite("How are you today?");

final Container<String> result = new Container<String>();

// Check the output on demand
tester.addOutputRead(new CDSTReadHandler<String>(){

    @Override
    public boolean read(String output) {
        if(output.equals("Good") || output.equals("Bad")){
            result.object = output;
            return true;
        } else {
            return false;
        }
    }
    
});

// Provide input on demand
tester.addInputWrite(new CDSTWriteHandler<String>() {

    @Override
    public String write() {
        if(result.object.equals("Good"))
            return "Great to hear!";
        else
            return "Oh I' sorry to hear that!";
    }
    
});

tester.addOutputRead("Yourself?");

tester.addInputWrite("Great, thanks for asking!");
```

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

## Example

There is an example program using the `CDSTester` class in the examples
package (the code above is taken from there).

It is a basic telnet server, so when launched you can telnet to it, and
interact with it to see how it catches undesireable behaviour.

## Features

* Designed for 2-Thread designs (for true duplex connections)
* *Use-Case and Test-Suite agnostic:* Can use JUnit or not, and use for
  anything, like TCP, Bluetooth, and any layer of abstraction for
  communication.
* Specify the test specification easily, synchronously.
* Can use custom types for each direction of the stream.
* (NEW) Can provide handlers (instead of specific instances) to write input and
  read + check output on demand.

## License

ISC License (ISC)

Copyright (c) 2014, Sam Lanning <sam@samlanning.com>

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
PERFORMANCE OF THIS SOFTWARE.
