s.makeGui

(
SynthDef("dynenv", {arg val = 0, dur = 1, ch = 0;
	Out.ar(ch, DynEnv.ar(val, dur));
}).add
)

(

var controlGroup = Group.new;
var soundGroup = Group.new;

var accBus = Bus.audio(s, 1);
var rotateBus = Bus.audio(s, 1);

var acc = Synth("dynenv", [\ch, accBus], controlGroup);
var rotate = Synth("dynenv", [\ch, rotateBus], controlGroup);

var interval = 200;
acc.set(\dur, (interval - 2) / 1000);
rotate.set(\dur, (interval - 2) / 1000);

soundGroup.moveAfter(controlGroup);

OSCFunc({|msg, time, addr, recvPort|
	var v;
	msg.removeAt(0);
	v = msg.collect({|x| x * x}).sum.sqrt;
	acc.set(\val, v);
}, '/acc', nil,1234);

OSCFunc({|msg, time, addr, recvPort|
	rotate.set(\val, msg.at(1));
}, '/rotate', nil,1234);

/*
{PMOsc.ar(
	440 * (In.ar(rotateBus) + 1).poll, 
	10 * In.ar(accBus).abs.scope, 3)}.play(soundGroup);
*/

// http://en.wikibooks.org/wiki/Designing_Sound_in_SuperCollider/Creaking_door

~woodfilter = { |input|
	var freqs, rqs, output;
	// Note: these freqs are as given in the diagram:
	freqs = [62.5, 125, 250, 395, 560, 790];
	// The Q values given in the diagram (we take reciprocal, since that's what BPF unit wants)
	rqs   = 1 / [1, 1, 2, 2, 3, 3];
	// in the text, andrew says that the freqs follow these ratios, 
	// which give a very different set of freqs...:
	// freqs = 125 * [0.5, 1, 1.58, 2.24, 2.92, 2, 2.55, 3.16];
 
	//Now let's apply the parallel bandpass filters, plus mix in a bit of the original:
	output = BPF.ar(input, freqs, rqs).sum + (input*0.2);
 
};

~stickslip = { |force|
	var inMotion, slipEvents, forceBuildup, evtAmp, evtDecayTime, evts;
	force = force.lag(0.1); // smoothing to get rid of volatile control changes
 
	inMotion = force > 0.1; // static friction: nothing at all below a certain force
 
	// slip events are generated at random with freqency proportional to force.
	// I originally used Dust to generate random events at a defined frequency, but
	// that lacks the slight "pitched" sound of the creaky door. Here we use Impulse
	// to generate a frequency, but we add some noise to its frequency to try and 
	// avoid it getting too perfectly regular.
	slipEvents = inMotion * Impulse.ar(force.linlin(0.1, 1, 1, 1/0.003) * LFDNoise1.ar(50).squared.linexp(-1,1, 0.5, 2));
 
	forceBuildup = Phasor.ar(slipEvents, 10 * SampleDur.ir, 0, inf).min(1);
 
	// Whenever a slip event happens we use Latch to capture the amount of
	// force that had built up.
	evtAmp = Latch.ar(Delay1.ar(forceBuildup.sqrt), slipEvents);
	evtDecayTime = evtAmp.sqrt;
	// The book applies square-root functions to shape the dynamic range of the events.
	// Remember that square-root is computationally intensive, so for efficient 
	// generation we might want to change it to (e.g.) a pre-calculated envelope.
 
	// Now we generate the events
	evts = EnvGen.ar(Env.perc(0.001, 1), slipEvents, evtAmp, 0, evtDecayTime * 0.01);
};
~squarepanel = { |input|
	var times, filt;
	// times in milliseconds, converted to seconds:
	times = [4.52, 5.06, 6.27, 8, 5.48, 7.14, 10.12, 16] * 0.001;
	filt = DelayC.ar(input, times, times).mean;
	filt = HPF.ar(filt, 125);
	filt * 4
};

{~squarepanel.value(~woodfilter.value(~stickslip.value(
//	LinLin.ar(In.ar(rotateBus).poll,0, 0.1, 0, 1))))}.play(soundGroup)
	LinLin.ar(In.ar(rotateBus).poll,0, 1, 0, 1))))}.play(soundGroup)

)