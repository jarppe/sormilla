sormilla
========

Playing with Leap Motion

# What is this?

Playing with [LeapMotion](http://www.leapmotion.com) using [Clojure](http://www.clojure.org).

# How to install?

You need a LeapMotion device and the [SDK](https://www.leapmotion.com/developers) (SDK license does not allow me to include it here). The SDK comes in tar-ball, extract it to somewhere and sym-link it to project root, like this:

```
$ git clone https://github.com/jarppe/sormilla.git
$ cd sormilla
$ ln -s /where/you/put/the/sdk/LeapSDK .
$ lein run
```

Linux, run these after ln -s ...
```
cd LeapSDK/lib/x64 # (or x86 depending on your arch)
cp libLeapJava.so libLeap.so ..
cd ../../..
```

See, that wasn't so hard, eh?
