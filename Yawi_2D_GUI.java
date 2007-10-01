/////////////////////////////////////////////////////////////////////
//
//		Yawi2D (Yet Another Wand for ImageJ 2D) - 2.1.0-SVN
//				http://yawi3d.sourceforge.net
//
// This is the selection tool (magic wand) used on 2D slices 
// to select ROIs. It uses an adaptive algorithm based on cromatic 
// composition of areas that segments regions with cromatic 
// similarities.
// The pluging provides a powerful implementation of the Wand 
// selection tool and it can be applied, with respect to ImageJ 
// wand, to a wider spectrum of problems.
//
// This software is released under GPL license, you can find a 
// copy of this license at http://www.gnu.org/copyleft/gpl.html
//
//
// Start date:
// 	2004-05-05
// Last update date:
// 	2007-09-20
//
// Authors:
//	Davide Coppola - dav_mc@users.sourceforge.net
//	Mario Rosario Guarracino - marioguarracino@users.sourceforge.net
//
/////////////////////////////////////////////////////////////////////

import ij.*;
import ij.io.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.*;

import java.awt.*;
import java.awt.Color;
import java.awt.FileDialog;
import java.awt.Menu;
import java.awt.event.*;
import java.io.*;

import java.lang.System;
import java.util.Arrays;

import javax.swing.ImageIcon;

public class Yawi_2D_GUI implements PlugIn
{
	/// image data/ pixels
	private byte[] img_pixels;
	/// image dimension
	private Dimension img_dim = new Dimension();

	private int new_type;

	/// lower threshold limit
	private int lower_threshold;
	/// upper threshold limit
	private int upper_threshold;
	/// radius of threshold rectangle
	private int rad_tresh;

	/// edge point
	private Point edge_p = new Point();
	/// initial direction of edge
	private int start_dir;
	/// directions
	static final int UP = 0, DOWN = 1, UP_OR_DOWN = 2, LEFT = 3, RIGHT = 4, LEFT_OR_RIGHT = 5, NA = 6;

	/// max number of points of a ROI. it is increased if necessary
	private int max_points = 1000;

	/// starting point, the point clicked by the user
	private Point start_p = new Point();

	/// X coordinate of the points in the border of the Roi
	private int[] xpoints = new int[max_points];
	/// Y coordinate of the points in the border of the Roi
	private int[] ypoints = new int[max_points];
	/// backup arrays - X coordinate
	private int[] xpoints_b;
	/// backup arrays - Y coordinate
	private int[] ypoints_b;
	/// number of points in the generated outline => dimension of xpoints and ypoints
	private int npoints = 0;

	/// flag that rapresents the status of the plugin (working or paused)
	private boolean working = false;

	/// generated ROI
	private	Roi roi = null;

	// default values for settings
	private static int RAD_DEF = 2;
	private static float PERC_DEF = 0.6f;
	private static int SIDE_DEF = 5;

	/// Inside - radius threshold
	private int _rad_ts = RAD_DEF;
	/// Inside - minimum percentage
	private float _min_perc = PERC_DEF;
	/// SetThreshold - side
	private int _side = SIDE_DEF;

	/// screen dimension
	Dimension screen_dim = Toolkit.getDefaultToolkit().getScreenSize();

	/// the main window
	MainWindow mw = null;

	/// the plugin directory of ImageJ
	static final String base_dir = IJ.getDirectory("plugins");
	/// Yawi2D directory
	static final String plugin_dir = base_dir + "Yawi_2D/";
	/// data directory of the plugin
	static final String data_dir = plugin_dir + "data/";
	/// icon image
	static final String icon_file = data_dir + "icon.png";
	/// histogram image
	static final String hist_file = data_dir + "hist.png";

	/// histogram left padding
	static final int HIST_XPAD = 4;
	/// histogram top padding
	static final int HIST_TOP_YPAD = 2;
	/// histogram bottom padding
	static final int HIST_YPAD = 132;
	/// histogram height limit for normal values
	static final int HIST_LIMIT_Y = 120;
	/// histogram height limit for mode value
	static final int HIST_MAX_Y = 129;
	/// number of colors of the image
	static final int HIST_COLORS = 256;

	// the plugin is running
	public void run(String arg)
	{
		// check for IJ version
		if (IJ.versionLessThan("1.37"))
			return;

                // open a new window and show it
		mw = new MainWindow("Yawi 2D");
		mw.setVisible(true);
    }

	/// main window
	class MainWindow extends ImageWindow implements ActionListener
	{
		private ImageProcessor ip;

		// -- GUI elements --
		private Menu file_menu = null;
		private Menu edit_menu = null;

		private Panel left_pan = null;
		private Panel right_pan = null;

		private TextArea txt_area = null;

		private Button b = null;
		private Button b2 = null;

		private ImagePlus hist = null;
		private HistCanvas hist_canv = null;
		private Label hist_info = null;
		// -- end GUI elements --

		private String last_file_dir = null;
		private String last_seq_dir = null;

		//private ConversionDialog d = null;

		MainWindow(String title)
		{
			super(title);

			// frame settings
			setLayout(new BorderLayout());
			setBackground(new Color(230, 230, 230));
 			setFont(new Font("verdana", Font.PLAIN, 12));

			// set an empty ImagePlus/ImageCanvas in order to avoid Exceptions
			// # bug in ImageWindow:drawInfo(Graphics g)
			imp = new ImagePlus();
			ic = new ImageCanvas(imp);

			// set listeners for the window
			addFocusListener(this);
			addWindowListener(this);

			// set default size of the window
			setSize(800, 600);

			// set the icon for the window
			// requires ImageIcon from SWING because of some bug in IJ Opener/ImagePlus classes
			setIconImage(new ImageIcon(icon_file).getImage());

			// menu data
			Menu menu = null;
			MenuItem item = null;
			ActionListener listener = null;

			// -- FILE MENU --
			file_menu = new Menu("File");

			// load an image
			item = new MenuItem("Open");
			listener = new FileOpenListener();
			item.addActionListener(listener);
			file_menu.add(item);

			// import image sequence
			item = new MenuItem("Import sequence");
			listener = new FileImpSeqListener();
			item.addActionListener(listener);
			file_menu.add(item);

			file_menu.addSeparator();

			// export a snapshot
			item = new MenuItem("Export snapshot");
			listener = new FileSnapListener();
			item.addActionListener(listener);
			item.setEnabled(false);
			file_menu.add(item);

			file_menu.addSeparator();

			// exit
			item = new MenuItem("Quit");
			listener = new FileExitListener();
			item.addActionListener(listener);
			file_menu.add(item);

			MenuBar menu_bar = new MenuBar();
			menu_bar.add(file_menu);
			// -- END FILE MENU --

			// -- EDIT MENU --
			edit_menu = new Menu("Edit");

			// smooth1 roi
			item = new MenuItem("Smooth1 Roi");
			listener = new EditSmooth1Listener();
			item.addActionListener(listener);
			// disable until an image is loaded
			item.setEnabled(false);
			edit_menu.add(item);

			// smooth2 roi
			item = new MenuItem("Smooth2 Roi");
			listener = new EditSmooth2Listener();
			item.addActionListener(listener);
			// disable until an image is loaded
			item.setEnabled(false);
			edit_menu.add(item);

			edit_menu.addSeparator();

			// Settings
			item = new MenuItem("Settings");
			listener = new SettingsListener();
			item.addActionListener(listener);
			// disable until an image is loaded
			item.setEnabled(false);
			edit_menu.add(item);

			menu_bar.add(edit_menu);
			// -- END EDIT MENU --

			// -- HELP MENU --
			menu = new Menu("Help");

			// about
			item = new MenuItem("About");
			listener = new HelpAboutListener();
			item.addActionListener(listener);
			menu.add(item);

			/// TODO
			// help
			item = new MenuItem("Help");
			listener = new HelpHelpListener();
			item.addActionListener(listener);
			menu.add(item);

			menu_bar.add(menu);
			// -- END HELP MENU --

			setMenuBar(menu_bar);

			// code required by IJ
			WindowManager.addWindow(this);
			WindowManager.setCurrentWindow(this);
		}

		ImagePlus GetImagePlus() { return imp; };

		/// build the GUI for image managing
		/// if with_stack is true, it's build a GUI for a stack of images
		/// if with_stack is false, it's build a GUI for a single image
		void BuildImgGUI(boolean with_stack)
		{
			// left column
			left_pan = new Panel();
			// image/stack panel
			right_pan = new InsetsPanel(10, 20, 10, 20);

			left_pan.setLayout(new GridLayout(3, 1));
			right_pan.setLayout(new BorderLayout());

			// command buttons main panel
			Panel left_pan_top = new Panel(new BorderLayout());
			// info main panel
			Panel left_pan_cen = new Panel(new BorderLayout());
			// histogram main panel
			Panel left_pan_bot = new Panel(new BorderLayout());

			// command heading
			Panel p1 = new Panel();
			// buttons panel
			Panel p2 = new Panel();
			// info heading
			Panel p3 = new Panel();
			// info text panel
			Panel txt_pan = new Panel();
			// histogram heading
			Panel p5 = new Panel();
			// histogram panel
			Panel p6 = new Panel(new BorderLayout());

			left_pan.setBackground(new Color(230, 230, 230));
			right_pan.setBackground(new Color(100, 100, 100));

			// commands heading
			p1.setBackground(new Color(200, 200, 200));
			// commands
			p2.setBackground(new Color(230, 230, 230));
			// txt heading
			p3.setBackground(new Color(200, 200, 200));
			// txt
			txt_pan.setBackground(new Color(230, 230, 230));
			// histogram heading
			p5.setBackground(new Color(200, 200, 200));
			// histogram
			p6.setBackground(new Color(255, 255, 255));

			// heading labels
			Label top_int = new Label("Commands", Label.CENTER);
			top_int.setFont(new Font("verdana", Font.BOLD, 12));
			Label cen_int = new Label("Results", Label.CENTER);
			cen_int.setFont(new Font("verdana", Font.BOLD, 12));
			Label bot_int = new Label("Histogram", Label.CENTER);
			bot_int.setFont(new Font("verdana", Font.BOLD, 12));

			// commands buttons
			b = new Button("Start");
			b.addActionListener(this);
			b2 = new Button("Stop");
			b2.addActionListener(this);
			b2.setEnabled(false);

			// build the command panel
			p1.add(top_int);
			p2.add(b);
			p2.add(b2);
			left_pan_bot.add(bot_int);

			// build the info panel
			p3.add(cen_int);
			// info text area
			txt_area = new TextArea("Yawi is not running\n", 10, 25, TextArea.SCROLLBARS_NONE);
			txt_pan.add(txt_area);

			// build the histogram panel
			p5.add(bot_int);

			Opener op = new Opener();
			hist = op.openImage(hist_file);
			hist_canv = new HistCanvas(hist);

			p6.add(hist_canv, BorderLayout.CENTER);

			hist_info = new Label(" ");
			p6.add(hist_info, BorderLayout.NORTH);

			// build the left column panel
			left_pan_top.add(p1, BorderLayout.NORTH);
			left_pan_top.add(p2, BorderLayout.CENTER);
			left_pan_cen.add(p3, BorderLayout.NORTH);
			left_pan_cen.add(txt_pan, BorderLayout.CENTER);
			left_pan_bot.add(p5, BorderLayout.NORTH);
			left_pan_bot.add(p6, BorderLayout.CENTER);
			left_pan.add(left_pan_top);
			left_pan.add(left_pan_cen);
			left_pan.add(left_pan_bot);

			// add the image
			right_pan.add(ic, BorderLayout.CENTER);

			// GUI with stack of images
			if(with_stack)
			{
				// add the slice selector
				Scrollbar slice_sel = new Scrollbar(Scrollbar.HORIZONTAL, 1, 1, 1, imp.getImageStackSize() + 1);
				slice_sel.setBackground(new Color(230, 230, 230));
				slice_sel.addAdjustmentListener(new ScrollListener());
				right_pan.add(slice_sel, BorderLayout.SOUTH);
			}

			// add main panels to the window
			add(left_pan, BorderLayout.WEST);
			add(right_pan, BorderLayout.CENTER);

			// enable File->Export Snapshot
			file_menu.getItem(3).setEnabled(true);
			// enable Edit items
			edit_menu.getItem(0).setEnabled(true);
			edit_menu.getItem(1).setEnabled(true);
			edit_menu.getItem(3).setEnabled(true);

			pack();
		}

		/// repaint the Histogram canvas
		public void RepaintHistogram() { hist_canv.repaint(); }

		/// print some info regarding the Histogram values (Color/Count)
		public void PrintHistogramInfo(int x, int y)
		{
			if(x >= HIST_XPAD  && x < (HIST_XPAD + HIST_COLORS) && y > HIST_TOP_YPAD && y < HIST_YPAD)
			{
				ByteStatistics stats = (ByteStatistics)(mw.GetImagePlus().getStatistics());

				// histogram data
				int[] hist = stats.histogram;

				hist_info.setText("Value: " + (x - HIST_XPAD) + " Count: " + hist[(x - HIST_XPAD)]);
			}
		}

		/// print some text in the text area of the window
		public void PrintInfo(String txt) { txt_area.setText(txt); }

		/// this method is called when a button in the command panel is pushed
		public void actionPerformed(ActionEvent e)
		{
			Object obj = e.getSource();

			// start is pressed => plugin working
			if(obj == b)
			{
				working = true;

				b.setEnabled(false);
				b2.setEnabled(true);

				txt_area.setText("Yawi is running");
			}
			// stop is pressed => plugin not working
			else if(obj == b2)
			{
				working = false;

				b.setEnabled(true);
				b2.setEnabled(false);

				txt_area.setText("Yawi is not running");

				mw.getCanvas().repaint();
			}
		}

		/// clear the GUI before to load a new image
		void ClearGUI()
		{
			if(left_pan != null && right_pan != null)
			{
				left_pan.setVisible(false);
				right_pan.setVisible(false);

				left_pan = null ;
				right_pan = null;

				imp = new ImagePlus();
				ic = new ImageCanvas(imp);
			}
		}

		/// load ad image and set the ImagePlus and ImageCanvas of the window
		boolean LoadImg(String file)
		{
			roi = null;

			Opener opener = new Opener();
			// load the image
			ImagePlus img = opener.openImage(file);

			// check if img has been loaded without problems
			if(img == null)
				return false;

			// default value
			new_type = ImagePlus.GRAY8;

			ConversionDialog d = new ConversionDialog(mw, "Convert the image", img.getType());

			if(img.getType() != new_type)
				ConvertImage(img, new_type);

			// store the ImagePlus
			imp = img;

			// update the ImageCanvas with the new Image
			ic = new ImgCanvas(imp);
			// store the ImageProcessor
			ip = imp.getProcessor();

			// save img dimension
			img_dim.setSize(ip.getWidth(), ip.getHeight());
			// save img pixels
			img_pixels = (byte[])ip.getPixels();

			if(img_dim.width > img_dim.height)
			{
				// image width is bigger than 80% of screen width
				if((double) (screen_dim.width * 0.80) < (double) img_dim.width)
					ic.setMagnification((double) ((double)(0.80 * screen_dim.width) / img_dim.width));
			}
			else
			{
				// image height is bigger than 80% of screen height
				if((double) (screen_dim.height * 0.80) < (double) img_dim.height)
					ic.setMagnification((double) ((double)(0.80 * screen_dim.height) / img_dim.height));
			}

			imp.setWindow(this);

			return true;
		}

		/// load an image sequence, all the images have to be of the same size and format
		boolean LoadImgSeq(String seq_dir)
		{
			roi = null;

			Dimension first_dim = new Dimension();

			Opener opener = new Opener();
			ImagePlus img = null;
			ImageStack stack = null;

			// loaded images
			int loaded_imgs = 0;

			// open the directory and sort the files in it
			File dir = new File(seq_dir);
			String [] files_list = dir.list();
			Arrays.sort(files_list);

			// default value
			new_type = ImagePlus.GRAY8;

			// load all the images in the dir
			for(int i= 0; i < files_list.length; i++)
			{
				img = opener.openImage(seq_dir + files_list[i]);

				if(img != null)
				{
					// no images loaded yet
					if(loaded_imgs == 0)
					{
						// the dimension of the first image is stored
						first_dim.setSize(img.getWidth(), img.getHeight());

						ConversionDialog d = new ConversionDialog(mw, "Convert the images", img.getType());

						// build the stack
						stack = new ImageStack(img.getWidth(), img.getHeight());
					}

					// if the dimension of the current img is different from the dimension of the
					// first one just skip it
					if(img.getWidth() == first_dim.width && img.getHeight() == first_dim.height)
					{
						if(img.getType() != new_type)
						ConvertImage(img, new_type);

						stack.addSlice(files_list[i], img.getProcessor());

						loaded_imgs++;
					}
					else
						IJ.write("Error loading \"" + files_list[i] + 
								"\"\nimage dimensions are different from the stack size, image skipped\n\n");
				}
			}

			if(loaded_imgs == 0)
				return false;
			else
			{
				imp = new ImagePlus("stack", stack);

				// update the ImageCanvas with the new Image
				ic = new ImgCanvas(imp);
				// store the ImageProcessor
				ip = imp.getProcessor();

				// save img dimension
				img_dim.setSize(ip.getWidth(), ip.getHeight());
				// save img pixels
				img_pixels = (byte[])ip.getPixels();

				if(img_dim.width > img_dim.height)
				{
					// image width is bigger than 80% of screen width
					if((double) (screen_dim.width * 0.80) < (double) img_dim.width)
						ic.setMagnification((double) ((double)(0.80 * screen_dim.width) / img_dim.width));
				}
				else
				{
					// image height is bigger than 80% of screen height
					if((double) (screen_dim.height * 0.80) < (double) img_dim.height)
						ic.setMagnification((double) ((double)(0.80 * screen_dim.height) / img_dim.height));
				}


				imp.setWindow(this);

				return true;
			}
		}

		/// convert an image to a new format according to new_type argument
		void ConvertImage(ImagePlus imp, int new_type)
		{
			ImageConverter conv = new ImageConverter(imp);

			// convert to 8-bit gray
			if(new_type == ImagePlus.GRAY8)
				conv.convertToGray8();
			// convert to 8-bit color
			else if(new_type == ImagePlus.COLOR_256)
			{
				if(imp.getType() != ImagePlus.COLOR_RGB)
					conv.convertToRGB();

				ImageConverter conv2 = new ImageConverter(imp);
				conv2.convertRGBtoIndexedColor(256);
			}
		}

		/// when a stack is loaded, set the slice to manage
		public void SetSlice(int ind)
		{
			imp.setSlice(ind);

			// store the ImageProcessor
			ip = imp.getProcessor();

			// save img pixels
			img_pixels = (byte[])ip.getPixels();
		}

		/// this listener is activated when the user select File->Open
		class FileOpenListener implements ActionListener
		{
			public void actionPerformed(ActionEvent e)
			{
				// show an AWT FileDialog to choose the file
				FileDialog fc = new FileDialog(mw, "Load a single image...");

				// set the directory to the last opened one
				if(last_file_dir != null)
					fc.setDirectory(last_file_dir);

				fc.setLocation(mw.getX() + 200, mw.getY() + 100);
				fc.setVisible(true);

				// store the img directory
				last_file_dir = fc.getDirectory();

				if(fc.getFile() != null)
				{
					String img_file = last_file_dir + fc.getFile();

					// clear the GUI
					ClearGUI();
					// load the image
					if(LoadImg(img_file))
						// make the GUI showing the loaded image
						BuildImgGUI(false);
					// ERROR
					else
						IJ.error("Error loading the file: " + img_file);
				}
				else
					IJ.error("Error, no file selected");
			}
		}

		// this listener is activated when the user select File->Import Sequence
		class FileImpSeqListener implements ActionListener
		{
			public void actionPerformed(ActionEvent e)
			{
				// show an AWT FileDialog to choose the file
				FileDialog fc = new FileDialog(mw, "Choose a directory...");

				// set the directory to the last opened one
				if(last_seq_dir != null)
					fc.setDirectory(last_seq_dir);

				fc.setLocation(mw.getX() + 200, mw.getY() + 100);

				fc.setVisible(true);

				// store the img directory
				last_seq_dir = fc.getDirectory();

				// clear the GUI
				ClearGUI();
				// load the image
				if(LoadImgSeq(last_seq_dir))
					// make the GUI showing the loaded image
					BuildImgGUI(true);
				else
					IJ.error("Error loading the stack from: " + last_seq_dir);
			}
		}

		// this listener is activated when the user select File->Export Snapshot
		class FileSnapListener implements ActionListener
		{
			// export a snapshot
			public void actionPerformed(ActionEvent e)
			{
				// default name for the file
				String exp_name = "snap.png";

				//creating new image and its processor
				ImagePlus exp_img = NewImage.createRGBImage(exp_name, img_dim.width, img_dim.height, 1, NewImage.FILL_BLACK);
				ImageProcessor exp_ip = exp_img.getProcessor();

				//copy original image into the new
				exp_ip.copyBits(ip, 0, 0, Blitter.COPY);

				//image saver
				FileSaver fs;
				//creating the filesaver
				fs = new FileSaver(exp_img);

				if(roi != null)
				{
					//set lines color
					exp_ip.setColor(Color.yellow);

					//draw ROI lines
					for(int i = 0; i < npoints-1 ; i++)
						exp_ip.drawLine(xpoints_b[i], ypoints_b[i], xpoints_b[i+1], ypoints_b[i+1]);

					exp_ip.drawLine(xpoints_b[npoints - 1], ypoints_b[npoints - 1], xpoints_b[0], ypoints_b[0]);
				}

				//save new image
				fs.saveAsPng();
			}
		}

		// this listener is activated when the user select File->Quit
		class FileExitListener implements ActionListener
		{
			// terminate the plugin
			public void actionPerformed(ActionEvent e) { dispose(); }
		}

		// this listener is activated when the user select Edit->Smooth1
		class EditSmooth1Listener implements ActionListener
		{
			// first algorithm for smoothing the Roi
			public void actionPerformed(ActionEvent e) { SmoothRoi1(); }
		}

		// this listener is activated when the user select Edit->Smooth2
		class EditSmooth2Listener implements ActionListener
		{
			// second algorithm for smoothing the Roi
			public void actionPerformed(ActionEvent e) { SmoothRoi2(); }
		}

		/// this listener is activated when the user select Edit->Settings
		class SettingsListener implements ActionListener
		{
			public void actionPerformed(ActionEvent e)
			{
				// show an AWT FileDialog to choose the file
				SettingsDialog sd = new SettingsDialog(mw, "Settings");
			}
		}

		// this listener is activated when the user select Help->About
		class HelpAboutListener implements ActionListener
		{
			// show the about dialog
			public void actionPerformed(ActionEvent e)
			{
				CenterDialog d = new CenterDialog(mw, "About...",
					"\t\tYawi2D 2.1.0\n\t    http://yawi3d.sourceforge.net/\n\n" +
					"Yawi2D (Yet Another Wand for ImageJ 2D) implements a selection tool for ImageJ " +
					"suitable for CT scanned images.\n" +
					"It helps in the selection of a 2D Region Of Interest (ROI) containing a lymphoma " +
					"(tumor mass).\n\n" +
					"\t\tAUTHORS:\n" +
					"Davide Coppola - dav_mc@users.sourceforge.net\n" +
					"Mario Rosario Guarracino - marioguarracino@users.sourceforge.net", false);

				d.setVisible(true);
			}
		}

		// this listener is activated when the user select Help->Help
		class HelpHelpListener implements ActionListener
		{
			// show the about dialog
			public void actionPerformed(ActionEvent e)
			{
				CenterDialog d = new CenterDialog(mw, "Help...",
					"To use the plugin on a single image:\n" +
					"1. File->Open and select the image file\n" +
					"2. Press the START button to start the plugin\n" +
					"3. Click on the area where the plugin has to make a ROI\n" +
					"4. Press the STOP button to stop the plugin and edit the ROI\n\n" +
					"To use the plugin on a sequence of images:\n" +
					"1. File->Import Sequence and select the directory containing the images\n" +
					"2. Press the START button to start the plugin\n" +
					"3. Use the slide bar to change the current image\n" +
					"4. Click on the area where the plugin has to make a ROI\n" +
					"5. Press the STOP button to stop the plugin and edit the ROI\n\n" +
					"To export a screenshot of the current image:\n" +
					"1. File->Export Snapshot\n" +
					"2. Set the name of the PNG file to export (i.e. \"snap.png\")\n" +
					"NOTE: an image/image sequence is required to perform this operation.\n\n" +
					"To improve the generated ROI:\n" +
					"1. Edit->Smooth1 Roi\n" +
					"2. Edit->Smooth2 Roi (this could be instable and mess up the ROI)\n\n" +
					"----------------------------------------\n\n" +
					"Settings data\n\n" +
					"- Threshold square dimension:\n" +
					"     dimension of the square used to set the threshold, usually an high value means a wider threshold range\n\n" +
					"- Outline search square dimension:\n" +
					"     dimension of the square used during the outline search, usually an high value means less accuracy in the search\n\n" +
					"- Outline search inside percentage:\n" +
					"     minimum percentage of pixels inside a square that have to be inside the threshold in order to consider the " +
					"square as inside a ROI, usually an high value means more accuracy\n\n", true);

				d.setVisible(true);
			}
		}

		/// listener of the slice selector
		class ScrollListener implements AdjustmentListener
		{
			public void adjustmentValueChanged(AdjustmentEvent e) 
			{
				SetSlice(e.getValue());
				mw.RepaintHistogram();
			}
		}

		/// a dialog for Yawi2D info/help
		public class CenterDialog extends Dialog
		{
			public CenterDialog(Frame parent, String title, String txt, boolean scroll)
			{
				super(parent, title, true);

				TextArea txt_area;

				if(scroll)
					txt_area = new TextArea(txt, 25, 55, TextArea.SCROLLBARS_VERTICAL_ONLY);
				else
					txt_area = new TextArea(txt, 15, 55, TextArea.SCROLLBARS_NONE);

				txt_area.setCaretPosition(0);
				txt_area.setEditable(false);

				txt_area.setBackground(new Color(240, 240, 240));
				add(txt_area);

				addWindowListener(new WindowAdapter()
				{
					public void windowClosing(WindowEvent event)
					{
						setVisible(false);
						dispose();
					}
				});

				pack();

				// center the dialog in the window
				setLocation(mw.getX() + (mw.getWidth() - getWidth()) / 2,
							mw.getY() + (mw.getHeight() - getHeight()) / 2);
			}
		}

		/// a dialog for file conversion
		public class ConversionDialog extends Dialog implements ActionListener
		{
			Checkbox c1;
			Checkbox c2;

			public ConversionDialog(Frame parent, String title, int type)
			{
				super(parent, title, true);

				setLayout(new BorderLayout());

				CheckboxGroup group = new CheckboxGroup();
				c1 = new Checkbox("8-bit gray", group, true);
				c2 = new Checkbox("8-bit color", group, false);

				Label l = new Label();

				switch(type)
				{
					case ImagePlus.GRAY8:
						l.setText("8-bit gray - Convert to:");
					break;

					case ImagePlus.GRAY16:
						l.setText("16-bit gray - Convert to:");
					break;

					case ImagePlus.GRAY32:
						l.setText("32-bit gray - Convert to:");
					break;

					case ImagePlus.COLOR_256:
						l.setText("8-bit color - Convert to:");
					break;

					case ImagePlus.COLOR_RGB:
						l.setText("32-bit color - Convert to:");
					break;

					default:
						l.setText("image type unknow - Convert to :");
					break;
				}

				add(l, BorderLayout.NORTH);

				Panel p = new Panel();
				p.setLayout(new GridLayout(2, 1));

				p.add(c1);
				p.add(c2);

				add(p, BorderLayout.CENTER);

				Button b = new Button("Convert");
				b.addActionListener(this);

				add(b, BorderLayout.SOUTH);

				pack();

				// center the dialog in the window
				setLocation(mw.getX() + (mw.getWidth() - getWidth()) / 2,
							mw.getY() + (mw.getHeight() - getHeight()) / 2);

				addWindowListener(new WindowAdapter()
				{
					public void windowClosing(WindowEvent event)
					{
						setVisible(false);
						dispose();
					}
				});

				setVisible(true);
			}

			// button pressed -> set the new_type value and close the dialog
			public void actionPerformed(ActionEvent e)
			{
				if(c1.getState())
					new_type = ImagePlus.GRAY8;
				else if(c2.getState())
					new_type = ImagePlus.COLOR_256;
				else
					new_type = ImagePlus.GRAY16;

				setVisible(false);
				dispose();
			}

			public Insets getInsets()
			{
				// top, left, bottom, right
				return (new Insets(20, 50, 20, 50));
			}
		}

		/// a dialog for some settings
		public class SettingsDialog extends Dialog implements ActionListener, AdjustmentListener
		{
			Scrollbar rad_sel;
			Scrollbar perc_sel;
			Scrollbar side_sel;

			Label v1;
			Label v2;
			Label v3;

			Button ok;
			Button reset;

			public SettingsDialog(Frame parent, String title)
			{
				super(parent, title, true);

				setLayout(new BorderLayout());

				InsetsPanel p1 = new InsetsPanel(10, 10, 10, 10);
				p1.setLayout(new GridLayout(3, 1));

				Label l3 = new Label("Threshold square dimension");
				Label l1 = new Label("Outline search square dimension");
				Label l2 = new Label("Outline search inside percentage");

				p1.add(l3);
				p1.add(l1);
				p1.add(l2);

				add(p1, BorderLayout.WEST);

				InsetsPanel p2 = new InsetsPanel(10, 20, 10, 20);
				p2.setLayout(new GridLayout(3, 1));
				((GridLayout)(p2.getLayout())).setVgap(10);

				// orientation, value, visible, min, max
 				side_sel = new Scrollbar(Scrollbar.HORIZONTAL, _side, 1, 2, 7);
 				rad_sel = new Scrollbar(Scrollbar.HORIZONTAL, _rad_ts, 1, 2, 6);
 				perc_sel = new Scrollbar(Scrollbar.HORIZONTAL, ((int)(_min_perc * 10)), 1, 3, 11);

				side_sel.setBlockIncrement(1);
				rad_sel.setBlockIncrement(1);
				perc_sel.setBlockIncrement(1);

				side_sel.addAdjustmentListener(this);
				rad_sel.addAdjustmentListener(this);
				perc_sel.addAdjustmentListener(this);

				p2.add(side_sel);
				p2.add(rad_sel);
				p2.add(perc_sel);

				add(p2, BorderLayout.CENTER);

				InsetsPanel p3 = new InsetsPanel(10, 10, 10, 10);
				p3.setLayout(new GridLayout(3, 1));

				v1 = new Label(String.valueOf(_rad_ts), Label.RIGHT);
				v2 = new Label(String.valueOf(((int)(_min_perc * 10))), Label.RIGHT);
				v3 = new Label(String.valueOf(_side), Label.RIGHT);

				p3.add(v3);
				p3.add(v1);
				p3.add(v2);

				add(p3, BorderLayout.EAST);

				InsetsPanel p4 = new InsetsPanel(10, 10, 10, 10);
				p4.setLayout(new GridLayout(1, 2));
				((GridLayout)(p4.getLayout())).setHgap(20);

				reset = new Button("Default");
				reset.addActionListener(this);
				ok = new Button("Ok");
				ok.addActionListener(this);

				p4.add(reset);
				p4.add(ok);

				add(p4, BorderLayout.SOUTH);

				pack();

				// center the dialog in the window
				setLocation(mw.getX() + (mw.getWidth() - getWidth()) / 2,
							mw.getY() + (mw.getHeight() - getHeight()) / 2);

				addWindowListener(new WindowAdapter()
				{
					public void windowClosing(WindowEvent event)
					{
						setVisible(false);
						dispose();
					}
				});

				setVisible(true);
			}

			// button pressed
			public void actionPerformed(ActionEvent e)
			{
				Object obj = e.getSource();

				// reset to default values
				if(obj == reset)
				{
					side_sel.setValue(SIDE_DEF);
					v3.setText(String.valueOf(SIDE_DEF));

					rad_sel.setValue(RAD_DEF);
					v1.setText(String.valueOf(RAD_DEF));

					perc_sel.setValue(((int)(PERC_DEF * 10)));
					v2.setText(String.valueOf(((int)(PERC_DEF * 10))));
				}
				// set setted values and exit
				else if(obj == ok)
				{
					_side = side_sel.getValue();
					_rad_ts = rad_sel.getValue();
					_min_perc = (float)(perc_sel.getValue() / 10.0f);

					setVisible(false);
					dispose();
				}
			}

			// scrollbar moved
			public void adjustmentValueChanged(AdjustmentEvent e)
			{
				Object obj = e.getSource();

				if(obj == side_sel)
					v3.setText(String.valueOf(side_sel.getValue()));
				else if(obj == rad_sel)
					v1.setText(String.valueOf(rad_sel.getValue()));
				else if(obj == perc_sel)
					v2.setText(String.valueOf(perc_sel.getValue()));

			}

			public Insets getInsets()
			{
				// top, left, bottom, right
				return (new Insets(20, 20, 20, 20));
			}
		}

	}

	/// this class extends a Panel in order to set the Insets space
	class InsetsPanel extends Panel
	{
		int _top;
		int _left;
		int _bottom;
		int _right;

		// top, left, bottom, right
		public InsetsPanel(int t, int l, int b, int r)
		{
			_top = t;
			_left = l;
			_bottom = b;
			_right = r;
		}

		public Insets getInsets()
		{
			return (new Insets(_top, _left, _bottom, _right));
		}
	}

	// this class extends the ImageCanvas class from IJ adding some features
	// needed by Yawi
	class ImgCanvas extends ImageCanvas
	{
		ImgCanvas(ImagePlus imp) { super(imp); }

		public void paint(Graphics g)
		{
			super.paint(g);

			if(roi != null && working)
				drawOverlay(g);
		}

		// draw some info on the image
		void drawOverlay(Graphics g)
		{
			g.setColor(Color.green);
			g.drawString("Roi from " + start_p.x + "," + start_p.y , 15, 15);
		}

		public void mouseReleased(MouseEvent e)
		{
			if(working)
				MakeROI(offScreenX(e.getX()), offScreenY(e.getY()));
			else
				super.mouseReleased(e);

			mw.RepaintHistogram();
		}
	}

	/// this class extends the ImageCanvas class from IJ adding some features
	/// needed by the histogram
	class HistCanvas extends ImageCanvas
	{
		HistCanvas(ImagePlus imp) { super(imp); }

		public void paint(Graphics g)
		{
			super.paint(g);

			drawHistogram(g);
		}

		/// draw the histogram plot
		void drawHistogram(Graphics g)
		{
			//g.setColor(Color.red);

			ByteStatistics stats = (ByteStatistics)(mw.GetImagePlus().getStatistics());

			// histogram data
			int[] hist = stats.histogram;
			// number of occurence for the mode color
			int occ_mode = 0;
			// number of occurrence for the second mode color
			int sec_occ_mode = 0;

			// compute the second mode color
			for(int k = 0; k < HIST_COLORS; k++)
			{
				if((hist[k] > sec_occ_mode) && (k != stats.mode))
					sec_occ_mode = hist[k];
			}

			// maxCount is too big
			if(stats.maxCount > (1.5 * sec_occ_mode))
				occ_mode = sec_occ_mode;
			else
				occ_mode = stats.maxCount;

			if(occ_mode == 0)
				occ_mode = 1;

			// normalize the data
			for(int k = 0; k < HIST_COLORS; k++)
				hist[k] = hist[k] * HIST_LIMIT_Y / occ_mode;

			// maxCount is too big so draw the MAX height in the graphic
			if(occ_mode == sec_occ_mode)
				hist[stats.mode] = HIST_MAX_Y;

			int r = 40;

			// draw the values
			for(int i = 0; i < HIST_COLORS; i++)
			{
				if((i % 26) == 0)
				{
					r += 20;
					g.setColor(new Color(r, 0, 0));
				}

				g.drawLine(HIST_XPAD + i, HIST_YPAD, HIST_XPAD + i, HIST_YPAD - hist[i]);
			}
		}

		/// when the mouse is moved on the plot show some info
		public void mouseMoved(java.awt.event.MouseEvent e)
		{
			mw.PrintHistogramInfo(e.getX(), e.getY());
		}
	}

	/// generate the ROI
	public void MakeROI(int x, int y)
	{
		start_p.setLocation(x, y);

		SetThreshold(x, y);
		AutoOutline(x, y);

		//there's a selection
		if(TraceEdge())
		{
			Roi previousRoi = (mw.GetImagePlus()).getRoi();
			roi = new PolygonRoi(xpoints, ypoints, npoints, Roi.TRACED_ROI);
			(mw.GetImagePlus()).killRoi();
			(mw.GetImagePlus()).setRoi(roi);

			if(previousRoi != null)
				roi.update(IJ.shiftKeyDown(), IJ.altKeyDown());

			PrintRoiInfo(roi);
			mw.RepaintHistogram();
		}
		else	//no selection
		{
			(mw.GetImagePlus()).killRoi();

			mw.PrintInfo("No selection avalaible, retry...");
		}
	}

	/// print some info about the obtained Roi using the text area of the GUI
	private void PrintRoiInfo(Roi roi)
	{
		Rectangle roi_rect = roi.getBounds();

		int roi_area = 0;

		// compute area of the ROI
		int start_x = (int)roi_rect.getX();
		int start_y = (int)roi_rect.getY();
		int end_x = (int)(roi_rect.getX() + roi_rect.getWidth());
		int end_y = (int)(roi_rect.getY() + roi_rect.getHeight());


		for(int x_roi = start_x; x_roi < end_x; x_roi++)
			for(int y_roi = start_y; y_roi < end_y; y_roi++)
				if(roi.contains(x_roi, y_roi))
					roi_area++;

		// get the perimeter of the ROI
		int len = (int)roi.getLength();

		String info = "                  ROI DATA\n\nx: " + start_x + "\ny: " + start_y +
					"\nwidth: " + ((int)roi_rect.getWidth()) + "\nheight: " + ((int)roi_rect.getHeight()) +
					"\narea: " + roi_area + "\nperimeter: " + len;

		mw.PrintInfo(info);
	}

	/// set the threshold of the ROI
	private void SetThreshold(int x, int y)
	{
		int dist = _side / 2;
		int color;

		int i,k;

		lower_threshold = 255;
		upper_threshold = 0;

		for(i = (y - dist); i <= (y + dist);i++)
		{
			for(k = (x - dist); k <= (x + dist);k++)
			{
				color = GetColor(k, i);

				if(color > upper_threshold)
					upper_threshold = color;
				else if(color < lower_threshold)
					lower_threshold = color;
			}
		}
	}

	/// return the color of a pixel located at (x,y)
	private int GetColor(int x, int y)
	{
		if(x >= 0 && y >= 0 && x < img_dim.width && y < img_dim.height)
			return img_pixels[(img_dim.width * y) + x] & 0xff;
		else
			return 0;
	}

	/// find ROI border starting from (start_x,start_y) point inside the area
	private void AutoOutline(int start_x, int start_y)
	{
		edge_p.setLocation(start_x, start_y);

		int direction = 0;

		if(Inside(edge_p.x, edge_p.y, RIGHT))
		{
			// if DELTAthreshold is very small we use the ImageJ inside
			if((upper_threshold - lower_threshold) < 5)
				do { edge_p.x++; } while(Inside(edge_p.x, edge_p.y) && edge_p.x < img_dim.width);
			else
			{
				do { edge_p.x++; } while(Inside(edge_p.x, edge_p.y, RIGHT) && edge_p.x < img_dim.width);
				// we are still into the threshold area
				if(Inside(edge_p.x, edge_p.y))
					do { edge_p.x++; } while(Inside(edge_p.x, edge_p.y) && edge_p.x < img_dim.width);
				// we are out the threshold area more than 1 pixel
				else if(!Inside(edge_p.x - 1, edge_p.y))
					do { edge_p.x--; } while(!Inside(edge_p.x, edge_p.y, LEFT) && edge_p.x > 0);
			}

			// initial direction
			if(!Inside(edge_p.x - 1, edge_p.y - 1))
				direction = RIGHT;
			else if(Inside(edge_p.x, edge_p.y - 1))
		 		direction = LEFT;
			else
		 		direction = DOWN;
		}
		else
		{
			// this case is not managed
		}

		// start direction is set for traceEdge
		start_dir = direction;
	}

	/// ImageJ inside, checks just 1 pixel
	/// check if the pixel color is inside the threshold or not
	private boolean Inside(int x, int y)
	{
		int value = -1;

		if(x >= 0 && y >= 0 && x < img_dim.width && y < img_dim.height)
			value = img_pixels[(img_dim.width * y) + x] & 0xff;

		return (value >= lower_threshold && value <= upper_threshold);
	}

	/// Yawi2D inside, checks a square area
	/// check if most of the pixels are inside the threshold or not
	private boolean Inside(int x, int y, int direction)
	{
		int x_a, x_b;
		int y_a, y_b;

		x_a = x_b = y_a = y_b = 0;


		// moving UP
		if(direction == UP)
		{
			if(x - _rad_ts > 0)
				x_a = x - _rad_ts;
			else
				x_a = 0;

			if(x + rad_tresh < img_dim.width)
				x_b = x + _rad_ts;
			else
				x_b = img_dim.width - 1;

			if(y-(_rad_ts * 2) > 0)
				y_a = y - (_rad_ts * 2);
			else
				y_a = 0;

			y_b = y;
		}

		// moving DOWN
		if(direction == DOWN)
		{
			if(x - _rad_ts > 0)
				x_a = x - _rad_ts;
			else
				x_a = 0;

			if(x + _rad_ts < img_dim.width)
		 		x_b = x + _rad_ts;
			else
				x_b = img_dim.width - 1;

			y_a = y;

			if(y + (_rad_ts * 2) < img_dim.height)
				y_b = y+(_rad_ts * 2);
			else
				y_b = img_dim.height - 1;
		}

		// moving LEFT
		if(direction == LEFT)
		{
			if(x - (2 * _rad_ts) > 0)
				x_a = x - (2 * _rad_ts);
			else
				x_a = 0;

			x_b = x;

			if(y - _rad_ts > 0)
				y_a = y - _rad_ts;
			else
				y_a = 0;

			if(y + _rad_ts < img_dim.height)
				y_b = y + _rad_ts;
			else
				y_b = img_dim.height - 1;
		}

		// moving RIGHT
		if(direction == RIGHT)
		{
			x_a = x;

			if(x+(2 * _rad_ts) < img_dim.width)
				x_b = x + (2 * _rad_ts);
			else
				x_b = img_dim.width - 1;

			if(y - _rad_ts > 0)
				y_a = y - _rad_ts;
			else
				y_a = 0;

			if(y + _rad_ts < img_dim.height)
				y_b = y + _rad_ts;
			else
				y_b = img_dim.height - 1;
		}

		int area = ((_rad_ts * 2) + 1)*((_rad_ts * 2) + 1);
		int inside_count = 0;
		int xp,yp;

		for(xp = x_a; xp <= x_b; xp++)
		{
			for(yp = y_a; yp <= y_b; yp++)
			{
					if(Inside(xp,yp))
						inside_count++;
			}
		}

		return (((float)inside_count) / area >= _min_perc);
	}

	/// traces an object defined by lower and upper threshold values.
	/// The boundary points are stored in the public xpoints and ypoints fields
	private boolean TraceEdge()
	{
		int secure = 0;

		int[] table =
		{
							// 1234 1=upper left pixel,  2=upper right, 3=lower left, 4=lower right
			NA, 			// 0000 should never happen
			RIGHT,			// 000X
			DOWN,			// 00X0
			RIGHT,			// 00XX
			UP,				// 0X00
			UP,				// 0X0X
			UP_OR_DOWN,		// 0XX0 Go up or down depending on current direction
			UP,				// 0XXX
			LEFT,			// X000
			LEFT_OR_RIGHT,  // X00X Go left or right depending on current direction
			DOWN,			// X0X0
			RIGHT,			// X0XX
			LEFT,			// XX00
			LEFT,			// XX0X
			DOWN,			// XXX0
			NA,				// XXXX Should never happen
		};

		int index;
		int new_direction;
		int x = edge_p.x;
		int y = edge_p.y;
		int direction = start_dir;

		// upper left
		boolean UL = Inside(x - 1, y - 1);
		// upper right
		boolean UR = Inside(x, y - 1);
		// lower left
		boolean LL = Inside(x - 1, y);
		// lower right
		boolean LR = Inside(x, y);

		int count = 0;

		do
		{
			index = 0;

			if(LR) index |= 1;
			if(LL) index |= 2;
			if(UR) index |= 4;
			if(UL) index |= 8;

			new_direction = table[index];

			// uncertainty, up or down
			if(new_direction == UP_OR_DOWN)
			{
				if(direction == RIGHT)
					new_direction = UP;
				else
					new_direction = DOWN;
			}

			// uncertainty, left or right
			if(new_direction == LEFT_OR_RIGHT)
			{
				if(direction == UP)
			   		new_direction = LEFT;
			 	else
				 	new_direction = RIGHT;
			}

			// error
		   	if(new_direction == NA)
				 return false;

			// a new direction means a new selection's point
			if(new_direction != direction)
		 	{
				xpoints[count] = x;
			 	ypoints[count] = y;
			 	count++;

				// xpoints and ypoints need more memory
			 	if(count == xpoints.length)
			 	{
					int[] xtemp = new int[max_points * 2];
				 	int[] ytemp = new int[max_points * 2];

				 	System.arraycopy(xpoints, 0, xtemp, 0, max_points);
				 	System.arraycopy(ypoints, 0, ytemp, 0, max_points);

				 	xpoints = xtemp;
				 	ypoints = ytemp;

				 	max_points *= 2;
				}
			}

			// moving along the selected direction
		  	switch(new_direction)
			{
				case UP:
		 	    	y = y - 1;
				 	LL = UL;
				 	LR = UR;
				 	UL = Inside(x - 1, y - 1);
				 	UR = Inside(x, y - 1);
				 	break;

			 	case DOWN:
				 	y = y + 1;
				 	UL = LL;
				 	UR = LR;
				 	LL = Inside(x - 1, y);
				 	LR = Inside(x, y);
				 	break;

 		 		case LEFT:
					x = x - 1;
				 	UR = UL;
				 	LR = LL;
				 	UL = Inside(x - 1, y - 1);
				 	LL = Inside(x - 1, y);
				 	break;

			 	case RIGHT:
				 	x = x + 1;
				 	UL = UR;
				 	LL = LR;
				 	UR = Inside(x, y - 1);
				 	LR = Inside(x, y);
				 	break;
			}

		  	direction = new_direction;

		 	if(secure < 10000)
				secure++;
		 	else	// traceEdge OVERFLOW!!!
				return false;

		} while ((x != edge_p.x || y != edge_p.y || direction != start_dir));

		// number of ROI points
	 	npoints = count;

		// backup ROI point
	 	xpoints_b = new int[npoints];
	 	ypoints_b = new int[npoints];

		for(int i = 0; i < npoints ; i++)
		{
				xpoints_b[i] = xpoints[i];
				ypoints_b[i] = ypoints[i];
		}

		return true;
	}

	/// first algorithm for smoothing the ROI
	private void SmoothRoi1()
	{
		// init new arrays
		int[] xpoints_smooth = new int[npoints];
		int[] ypoints_smooth = new int[npoints];

		boolean search = true;
		boolean found = false;
		int s_ind;
		int smooth_points = 0;

		for(int i = 0; i < npoints; i++)
		{
			s_ind = i + 1;

			while(s_ind < npoints && !found)
			{
				// found an equal point
				if(xpoints_b[i] == xpoints_b[s_ind] && ypoints_b[i] == ypoints_b[s_ind])
					found = true;

				s_ind++;
			}

			if(found)
			{
				s_ind--;

				found = false;
				i = s_ind;
			}

			xpoints_smooth[smooth_points] = xpoints_b[i];
			ypoints_smooth[smooth_points] = ypoints_b[i];

			smooth_points++;
		}

		// store the smoothed Roi coordinates
		for(int i = 0; i < smooth_points; i++)
		{
			xpoints_b[i] = xpoints_smooth[i];
			ypoints_b[i] = ypoints_smooth[i];
		}

		npoints = smooth_points;
		roi = new PolygonRoi(xpoints_smooth, ypoints_smooth, smooth_points, Roi.TRACED_ROI);
		(mw.GetImagePlus()).setRoi(roi);
	}

	/// second algorithm for smoothing the ROI
	private void SmoothRoi2()
	{
		//make new arrays
		int[] xpoints_smooth = new int[npoints];
			int[] ypoints_smooth = new int[npoints];

		boolean search = true;
		boolean found = false;
		int s_ind;
		int smooth_points = 0;

		int cur_ind = 0;

		while(cur_ind < npoints)
		{
			//copy a point
			xpoints_smooth[smooth_points] = xpoints_b[cur_ind];
			ypoints_smooth[smooth_points] = ypoints_b[cur_ind];
			smooth_points++;

			s_ind = cur_ind + 1;

			// look for a close point
			while(s_ind < npoints && !found)
			{
				// found a close point
				if((xpoints_b[cur_ind] == xpoints_b[s_ind] &&
					Math.abs(ypoints_b[cur_ind] - ypoints_b[s_ind]) == 2) ||
				   (ypoints_b[cur_ind] == ypoints_b[s_ind] &&
					Math.abs(xpoints_b[cur_ind] - xpoints_b[s_ind]) == 2))
					found = true;

				s_ind++;
			}

			// close point found
			if(found)
			{
				s_ind--;

				found = false;
				cur_ind = s_ind;
			}
			else
				cur_ind++;

			if(cur_ind < npoints)
			{
				xpoints_smooth[smooth_points] = xpoints_b[cur_ind];
				ypoints_smooth[smooth_points] = ypoints_b[cur_ind];

				smooth_points++;
				cur_ind++;
			}
		}

		for(int i = 0; i < smooth_points; i++)
		{
			xpoints_b[i] = xpoints_smooth[i];
			ypoints_b[i] = ypoints_smooth[i];
		}

		npoints = smooth_points;

		roi = new PolygonRoi(xpoints_smooth, ypoints_smooth, smooth_points, Roi.TRACED_ROI);
		(mw.GetImagePlus()).setRoi(roi);
	}
}
