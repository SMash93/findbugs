/*
 * FindBugs - Find Bugs in Java programs
 * Copyright (C) 2006, University of Maryland
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston MA 02111-1307, USA
 */

package edu.umd.cs.findbugs.gui2;

import java.awt.Color;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import javax.swing.JTextPane;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;

import edu.umd.cs.findbugs.BugAnnotation;
import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.ba.SourceFile;
import edu.umd.cs.findbugs.sourceViewer.JavaSourceDocument;

public final class SourceCodeDisplay implements Runnable {
	final MainFrame frame;

	static final Color MAIN_HIGHLIGHT = new Color(1f, 1f, 0.5f);
	static final Color MAIN_HIGHLIGHT_MORE = MAIN_HIGHLIGHT.brighter();
	static final Color ALTERNATIVE_HIGHLIGHT = new Color(0.86f, 0.90f, 1.0f);

	public static final Document SOURCE_NOT_RELEVANT = new DefaultStyledDocument();;
	
	Map<String, JavaSourceDocument> map = new HashMap<String, JavaSourceDocument>();

	SourceCodeDisplay(MainFrame frame) {
		this.frame = frame;
		Thread t = new Thread(this, "Source code display thread");
		t.setDaemon(true);
		t.start();
	}

	boolean pendingUpdate;

	@CheckForNull BugInstance bugToDisplay;

	SourceLineAnnotation sourceToHighlight;

	public synchronized void displaySource(BugInstance bug,
			SourceLineAnnotation source) {
		bugToDisplay = bug;
		sourceToHighlight = source;
		pendingUpdate = true;
		notifyAll();
	}

	public void clearCache() {
		map.clear();
	}
	
	@NonNull
	JavaSourceDocument getDocument(SourceLineAnnotation source) {
		try {
			SourceFile sourceFile = frame.sourceFinder.findSourceFile(source);
			String fullFileName = sourceFile.getFullFileName();
			JavaSourceDocument result = map.get(fullFileName);
			if (result != null)
				return result;
			try {
				InputStream in = sourceFile.getInputStream();
				result = new JavaSourceDocument(source.getClassName(),
						new InputStreamReader(in));
			} catch (Exception e) {
				result = JavaSourceDocument.UNKNOWNSOURCE;
				Debug.println(e); // e.printStackTrace();
			}
			map.put(fullFileName, result);
			return result;
		} catch (Exception e) {
			Debug.println(e); // e.printStackTrace();
			return JavaSourceDocument.UNKNOWNSOURCE;
		
		}
	}

	public void run() {
		while (true) {
			BugInstance myBug;
			SourceLineAnnotation mySourceLine;
			synchronized (this) {
				while (!pendingUpdate) {
					try {
						wait();
					} catch (InterruptedException e) {
						// we don't use these
					}
				}
				myBug = bugToDisplay;
				mySourceLine = sourceToHighlight;
				bugToDisplay = null;
				sourceToHighlight = null;
				pendingUpdate = false;
			}
			if (myBug == null) {
				frame.clearIndividualBugInformation();
				continue;
			}
			
			try {
			final JavaSourceDocument src = getDocument(mySourceLine);
			src.getHighlightInformation().clear();
			String primaryKind = mySourceLine.getDescription();
			// Display myBug and mySourceLine
			for(Iterator<BugAnnotation> i = myBug.annotationIterator(); i.hasNext(); ) {
				BugAnnotation annotation = i.next();
				if (annotation instanceof SourceLineAnnotation) {
					SourceLineAnnotation sourceAnnotation = (SourceLineAnnotation) annotation;
					if (sourceAnnotation == mySourceLine)  continue;
					if (sourceAnnotation.getDescription().equals(primaryKind))
						highlight(src, sourceAnnotation, MAIN_HIGHLIGHT_MORE);
					else 
						highlight(src, sourceAnnotation, ALTERNATIVE_HIGHLIGHT);
				}
			}
			highlight(src, mySourceLine, MAIN_HIGHLIGHT);
			final BugInstance thisBug = myBug;
			final SourceLineAnnotation thisSource = mySourceLine;
			javax.swing.SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					frame.sourceCodeTextPane.setEditorKit(src.getEditorKit());
					StyledDocument document = src.getDocument();
					frame.sourceCodeTextPane.setDocument(document);
					frame.setSourceTabTitle(thisSource.getSourceFile() + " in " + thisSource.getPackageName());
					int startLine = thisSource.getStartLine();
					int endLine = thisSource.getEndLine();
					int originLine = (startLine + endLine) / 2;
					LinkedList<Integer> otherLines = new LinkedList<Integer>();
					//show(frame.sourceCodeTextPane, document, thisSource);
					for(Iterator<BugAnnotation> i = thisBug.annotationIterator(); i.hasNext(); ) {
						BugAnnotation annotation = i.next();
						if (annotation instanceof SourceLineAnnotation) {
							SourceLineAnnotation sourceAnnotation = (SourceLineAnnotation) annotation;
							if (sourceAnnotation != thisSource) {
								//show(frame.sourceCodeTextPane, document, sourceAnnotation);
								int otherLine = sourceAnnotation.getStartLine();
								if (otherLine > originLine) otherLine = sourceAnnotation.getEndLine();
								otherLines.add(otherLine);
							}
						}
					}
					//show(frame.sourceCodeTextPane, document, thisSource);
					frame.sourceCodeTextPane.scrollLinesToVisible(startLine, endLine, otherLines);
				}
			});
		} catch (Exception e) {
			Debug.println(e); // e.printStackTrace();
		}
		}
	}

	/**
	 * @param src
	 * @param sourceAnnotation
	 */
	private void highlight(JavaSourceDocument src, SourceLineAnnotation sourceAnnotation, Color color) {

		int startLine = sourceAnnotation.getStartLine();
		if (startLine == -1) return;
		src.getHighlightInformation().setHighlight(startLine, sourceAnnotation.getEndLine(), color);
	}

	private void show(JTextPane pane, Document src, SourceLineAnnotation sourceAnnotation) {

		int startLine = sourceAnnotation.getStartLine();
		if (startLine == -1) return;
		frame.sourceCodeTextPane.scrollLineToVisible(startLine);
		/*
		Element element = src.getDefaultRootElement().getElement(sourceAnnotation.getStartLine()-1);
		if (element == null) {
			if (MainFrame.DEBUG) {
			System.out.println("Couldn't display line " + sourceAnnotation.getStartLine() + " of " + sourceAnnotation.getSourceFile());
			System.out.println("It only seems to have " + src.getDefaultRootElement().getElementCount() + " lines");
			}
			return;
		}
		pane.setCaretPosition(element.getStartOffset());
		*/
	}

	public void showLine(int line) {
		frame.sourceCodeTextPane.scrollLineToVisible(line);
		/*
		JTextPane pane = frame.sourceCodeTextPane;
		Document doc = pane.getDocument();
		Element element = doc.getDefaultRootElement().getElement(line-1);
		pane.setCaretPosition(element.getStartOffset());
		*/
	}
}
